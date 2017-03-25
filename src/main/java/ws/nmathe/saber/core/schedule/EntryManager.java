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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;


/**
 * Manages the schedule of ScheduleEntries for all attached guilds
 * Responsible for checking for expired entries, syncing channels to
 * their respective google calendar address, managing IDs, and removing/creating
 * entries.
 */
public class EntryManager
{
    /**
     * creates the scheduledExecutor thread pool and starts schedule timers which
     * check for expired entries
     */
    public void init()
    {
        // create thread every minute to start/end/remind entries
        ScheduledExecutorService scheduler1 = Executors.newScheduledThreadPool(1);
        scheduler1.scheduleAtFixedRate( new EntryProcessor(0),
                0, 30, TimeUnit.SECONDS );

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

    public void newEntry(String title, ZonedDateTime start, ZonedDateTime end, ArrayList<String> comments,
                         int repeat, String url, TextChannel channel)
    {
        List<Date> reminders = new ArrayList<>();
        for(Integer til : Main.getScheduleManager().getDefaultReminders(channel.getId()))
        {
            if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
            {
                reminders.add(Date.from(start.toInstant().minusSeconds(til*60)));
            }
        }

        Integer newId = this.newId(null);
        Message message = MessageGenerator.generate(title, start, end, comments, repeat,
                url, reminders, newId, channel.getId());

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
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().insertOne(entryDocument);
        });
    }

    public void updateEntry(Integer entryId, String title, ZonedDateTime start, ZonedDateTime end,
                            ArrayList<String> comments, int repeat, String url, Message origMessage)
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
                url, reminders, entryId, origMessage.getChannel().getId());
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
                            .append("hasStarted", false)
                            .append("messageId", msg.getId())
                            .append("channelId", channelId)
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().replaceOne(eq("_id", entryId), entryDocument);
        });
    }

    public void removeEntry( Integer entryId )
    {
        Main.getDBDriver().getEventCollection().findOneAndDelete(eq("_id", entryId));
    }

    /**
     * regenerates the displayed Message text for a schedule entry
     *
     * @param eId integer Id
     */
    public void reloadEntry( Integer eId )
    {
        ScheduleEntry se = getEntry( eId );
        if( se == null ) return;
        se.adjustTimer();
    }

    private Integer newId( Integer oldId )
    {
        // try first to use the requested Id
        Integer ID;
        if (oldId == null)
            ID = (int) Math.ceil(Math.random() * (Math.pow(2, 32) - 1));
        else
            ID = oldId;

        // if the Id is in use, generate a new one until a free one is found
        while (Main.getDBDriver().getEventCollection().find(eq("_id", ID)).first() != null)
        {
            ID = (int) Math.ceil(Math.random() * (Math.pow(2, 32) - 1));
        }

        return ID;
    }

    public ScheduleEntry getEntry(Integer entryId)
    {
        Document entryDocument = Main.getDBDriver().getEventCollection()
                .find(eq("_id", entryId)).first();

        if (entryDocument != null)
            return new ScheduleEntry(entryDocument);

        else    // otherwise return null
            return null;
    }

    public ScheduleEntry getEntryFromGuild(Integer entryId, String guildId)
    {
        Document entryDocument = Main.getDBDriver().getEventCollection()
                .find(and(eq("_id", entryId), eq("guildId",guildId))).first();

        if (entryDocument != null)
            return new ScheduleEntry(entryDocument);

        else    // otherwise return null
            return null;
    }

    public boolean isLimitReached(String gId)
    {
        long count = Main.getDBDriver().getEventCollection().count(eq("guildId",gId));
        return Main.getBotSettings().getMaxEntries() < count;
    }
}
