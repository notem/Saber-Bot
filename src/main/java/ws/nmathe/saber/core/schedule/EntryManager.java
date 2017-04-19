package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;


/**
 * Manages the full listing of entries for all attached guilds
 * Responsible for checking for expired entries, syncing channels to
 * their respective google calendar address, managing IDs, and removing/creating
 * entries.
 */
public class EntryManager
{
    private Random generator;

    public EntryManager()
    {
        this.generator = new Random(Instant.now().toEpochMilli());
    }

    /**
     * creates the scheduledExecutor thread pool and starts schedule timers which
     * check for expired entry timers and adjust the message display timer
     */
    public void init()
    {
        // create thread every minute to start/end/remind entries
        ScheduledExecutorService scheduler1 = Executors.newScheduledThreadPool(1);
        scheduler1.scheduleAtFixedRate( new EntryProcessor(0),
                0, 30, TimeUnit.SECONDS);

        // thread to adjust entry display timers
        ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);
        // 1 day timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(3),
                0, 24*60*60, TimeUnit.SECONDS);
        // 1 hour timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(2),
                0, 60*60, TimeUnit.SECONDS);
        // 5 min timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(1),
                30 , 60*3, TimeUnit.SECONDS );
    }

    /**
     * Create a new entry on a schedule
     * @param title     (String) title
     * @param start     (ZonedDateTime) datetime event starts
     * @param end       (ZonedDateTime) datetime event ends
     * @param comments  (List of String)
     * @param repeat    (int) binary representation of days event repeats
     * @param url       (String) url for title
     * @param channel   (MessageChannel) channel to send event's message
     */
    public void newEntry(String title, ZonedDateTime start, ZonedDateTime end, List<String> comments,
                         int repeat, String url, TextChannel channel, String googleId)
    {
        List<Date> reminders = new ArrayList<>();
        for(Integer til : Main.getScheduleManager().getDefaultReminders(channel.getId()))
        {
            if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
            {
                reminders.add(Date.from(start.toInstant().minusSeconds(til*60)));
            }
        }

        Integer newId = this.newId();
        Message message = MessageGenerator.generate(title, start, end, comments, repeat,
                url, reminders, newId, channel.getId(), channel.getGuild().getId());

        MessageUtilities.sendMsg(message, channel, msg -> {
            String guildId = msg.getGuild().getId();
            String channelId = msg.getChannel().getId();

            Document entryDocument =
                    new Document("_id", newId)
                            .append("title", title)
                            .append("start", Date.from(start.toInstant()))
                            .append("end", Date.from(end.toInstant()))
                            .append("zone", start.getZone().getId())
                            .append("comments", comments)
                            .append("repeat", repeat)
                            .append("reminders", reminders)
                            .append("url", url)
                            .append("hasStarted", false)
                            .append("messageId", msg.getId())
                            .append("channelId", channelId)
                            .append("googleId", googleId)
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().insertOne(entryDocument);
            Main.getScheduleManager().sortSchedule(channelId);
        });
    }

    /**
     * Update an entry with a new configuration
     * @param entryId   (Integer) ID of the entry
     * @param title     (String) title
     * @param start     (ZonedDateTime) datetime event starts
     * @param end       (ZonedDateTime) datetime event ends
     * @param comments  (List of String)
     * @param repeat    (int) binary representation of days event repeats
     * @param url       (String) url for title
     * @param origMessage (Message) event's message to be updated
     */
    public void updateEntry(Integer entryId, String title, ZonedDateTime start, ZonedDateTime end,
                            List<String> comments, int repeat, String url, boolean hasStarted,
                            Message origMessage, String googleId)
    {
        List<Date> reminders = new ArrayList<>();
        for(Integer til : Main.getScheduleManager().getDefaultReminders(origMessage.getChannel().getId()))
        {
            if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
            {
                reminders.add(Date.from(start.toInstant().minusSeconds(til*60)));
            }
        }

        Message message = MessageGenerator.generate(title, start, end, comments, repeat,
                url, reminders, entryId, origMessage.getChannel().getId(), origMessage.getGuild().getId());

        MessageUtilities.editMsg(message, origMessage, msg -> {
            String guildId = msg.getGuild().getId();
            String channelId = msg.getChannel().getId();

            Document entryDocument =
                    new Document("_id", entryId)
                            .append("title", title)
                            .append("start", Date.from(start.toInstant()))
                            .append("end", Date.from(end.toInstant()))
                            .append("zone", start.getZone().getId())
                            .append("comments", comments)
                            .append("repeat", repeat)
                            .append("reminders", reminders)
                            .append("url", url)
                            .append("hasStarted", hasStarted)
                            .append("messageId", msg.getId())
                            .append("channelId", channelId)
                            .append("googleId", googleId)
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().replaceOne(eq("_id", entryId), entryDocument);
            Main.getScheduleManager().sortSchedule(channelId);
        });
    }

    /**
     * removes an entry by id from the db
     * @param entryId (Integer) ID of event entry
     */
    public void removeEntry( Integer entryId )
    {
        Main.getDBDriver().getEventCollection().findOneAndDelete(eq("_id", entryId));
    }

    /**
     * regenerates the displayed Message text for a schedule entry
     * @param eId integer Id
     */
    public void reloadEntry( Integer eId )
    {
        ScheduleEntry se = getEntry( eId );
        if( se == null ) return;
        se.reloadDisplay();
    }

    /**
     * generates a new ID randomly from a 32bit space
     * @return (Integer) new, unused id
     */
    private Integer newId()
    {
        // try first to use the requested Id
        Integer ID;
        ID = (int) Math.ceil(generator.nextDouble() * (Math.pow(2, 32) - 1));

        // if the Id is in use, generate a new one until a free one is found
        while (Main.getDBDriver().getEventCollection().find(eq("_id", ID)).first() != null)
        {
            ID = (int) Math.ceil(generator.nextDouble() * (Math.pow(2, 32) - 1));
        }

        return ID;
    }

    /**
     * Finds an event and returns it's newly created class object if it exists
     * @param entryId (Integer) event ID
     * @return (EventEntry) of event if exists, otherwise null
     */
    public ScheduleEntry getEntry(Integer entryId)
    {
        Document entryDocument = Main.getDBDriver().getEventCollection()
                .find(eq("_id", entryId)).first();

        if (entryDocument != null)
            return new ScheduleEntry(entryDocument);

        else    // otherwise return null
            return null;
    }

    /**
     * Finds an event by id that also belongs to a specific guild
     * @param entryId (Integer) event ID
     * @param guildId (String) guild ID
     * @return (EventEntry) if exists, otherwise null
     */
    public ScheduleEntry getEntryFromGuild(Integer entryId, String guildId)
    {
        Document entryDocument = Main.getDBDriver().getEventCollection()
                .find(and(eq("_id", entryId), eq("guildId",guildId))).first();

        if (entryDocument != null)
            return new ScheduleEntry(entryDocument);

        else    // otherwise return null
            return null;
    }

    /**
     * has a guild reached it's maximum event limit?
     * @param gId (String) guild ID
     * @return (boolean)
     */
    public boolean isLimitReached(String gId)
    {
        long count = Main.getDBDriver().getEventCollection().count(eq("guildId",gId));
        return Main.getBotSettingsManager().getMaxEntries() < count;
    }
}
