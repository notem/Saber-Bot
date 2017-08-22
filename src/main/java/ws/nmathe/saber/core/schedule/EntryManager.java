package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
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
        scheduler1.scheduleAtFixedRate( new EntryProcessor(0), 60, 60, TimeUnit.SECONDS);

        // thread to adjust entry display timers
        ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);
        // 1 day timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(3), 12*60*60, 12*60*60, TimeUnit.SECONDS);
        // 1 hour timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(2), 60*30, 60*30, TimeUnit.SECONDS);
        // 4.5 min timer
        scheduler2.scheduleAtFixedRate( new EntryProcessor(1), 60*4+30, 60*3, TimeUnit.SECONDS );
    }

    /**
     * Create a new entry on a schedule
     * @param se (ScheduleEntry) the base ScheduleEntry object to use
     */
    public Integer newEntry(ScheduleEntry se)
    {
        // identify which shard is responsible for the schedule
        String guildId = se.getGuildId();
        String channelId = se.getChannelId();
        JDA jda = Main.getShardManager().isSharding() ? Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();

        // generate event reminders from schedule settings
        List<Date> reminders = new ArrayList<>();
        for(Integer til : Main.getScheduleManager().getDefaultReminders(se.getChannelId()))
        {
            if(Instant.now().until(se.getStart(), ChronoUnit.MINUTES) > til)
            {
                reminders.add(Date.from(se.getStart().toInstant().minusSeconds(til*60)));
            }
        }
        se.setReminders(reminders);

        // process expiration date
        Date expire;
        if (se.getExpire() == null)
        {
            expire = null;
        }
        else
        {
            expire = Date.from(se.getExpire().toInstant());
        }

        // is rsvp enabled on the channel set empty rsvp lists
        if( Main.getScheduleManager().isRSVPEnabled(se.getChannelId()) )
        {
            se.setRsvpYes(new ArrayList<>())
                    .setRsvpNo(new ArrayList<>())
                    .setRsvpUndecided(new ArrayList<>());
        }

        // generate event display message
        se.setId(this.newId());

        Message message = MessageGenerator.generate(se);

        // send message to schedule
        TextChannel channel = jda.getTextChannelById(channelId);
        MessageUtilities.sendMsg(message, channel, msg ->
        {
            // add reaction options if rsvp is enabled
            if( Main.getScheduleManager().isRSVPEnabled(channelId) )
            {
                msg.addReaction(Main.getBotSettingsManager().getYesEmoji()).queue();
                msg.addReaction(Main.getBotSettingsManager().getNoEmoji()).queue();
                msg.addReaction(Main.getBotSettingsManager().getClearEmoji()).queue();
            }

            // add new document
            Document entryDocument =
                    new Document("_id", se.getId())
                            .append("title", se.getTitle())
                            .append("start", Date.from(se.getStart().toInstant()))
                            .append("end", Date.from(se.getEnd().toInstant()))
                            .append("comments", se.getComments())
                            .append("repeat", se.getRepeat())
                            .append("reminders", reminders)
                            .append("url", se.getTitleUrl())
                            .append("hasStarted", se.hasStarted())
                            .append("messageId", msg.getId())
                            .append("channelId", channelId)
                            .append("googleId", se.getGoogleId())
                            .append("rsvp_yes", se.getRsvpYes())
                            .append("rsvp_no", se.getRsvpNo())
                            .append("rsvp_undecided", se.getRsvpUndecided())
                            .append("start_disabled", false)
                            .append("end_disabled", false)
                            .append("reminders_disabled", false)
                            .append("rsvp_max", -1)
                            .append("expire", expire)
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().insertOne(entryDocument);

            // auto-sort
            int sortType = Main.getScheduleManager().getAutoSort(channelId);
            if(sortType == 1)
            {
                Main.getScheduleManager().sortSchedule(channelId, false);
            }
            if(sortType == 2)
            {
                Main.getScheduleManager().sortSchedule(channelId, true);
            }
        });

        return se.getId();
    }

    /**
     * Update an entry with a new configuration
     * All schedule entry parameters should be filled.
     * The ID of the ScheduleEntry must not have been changed.
     * @param se (ScheduleEntry) the new schedule entry object
     */
    public void updateEntry(ScheduleEntry se)
    {

        Message origMessage = se.getMessageObject();
        if(origMessage == null) return;

        ZonedDateTime start = se.getStart();
        ZonedDateTime expireDate = se.getExpire();

        // generate event reminders from schedule settings
        List<Date> reminders = new ArrayList<>();
        for(Integer til : Main.getScheduleManager().getDefaultReminders(origMessage.getChannel().getId()))
        {
            if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
            {
                reminders.add(Date.from(start.toInstant().minusSeconds(til*60)));
            }
        }

        // process expiration date
        Date expire;
        if (expireDate == null)
        {
            expire = null;
        }
        else
        {
            expire = Date.from(expireDate.toInstant());
        }

        // generate event display message
        Message message = MessageGenerator.generate(se);

        // update message display
        MessageUtilities.editMsg(message, origMessage, msg ->
        {
            String guildId = msg.getGuild().getId();
            String channelId = msg.getChannel().getId();

            // replace whole document
            Document entryDocument =
                    new Document("_id", se.getId())
                            .append("title", se.getTitle())
                            .append("start", Date.from(start.toInstant()))
                            .append("end", Date.from(se.getEnd().toInstant()))
                            .append("comments", se.getComments())
                            .append("repeat", se.getRepeat())
                            .append("reminders", reminders)
                            .append("url", se.getTitleUrl())
                            .append("hasStarted", se.hasStarted())
                            .append("messageId", msg.getId())
                            .append("channelId", channelId)
                            .append("googleId", se.getGoogleId())
                            .append("rsvp_yes", se.getRsvpYes())
                            .append("rsvp_no", se.getRsvpNo())
                            .append("rsvp_undecided", se.getRsvpUndecided())
                            .append("start_disabled", se.isQuietStart())
                            .append("end_disabled", se.isQuietEnd())
                            .append("reminders_disabled", se.isQuietRemind())
                            .append("rsvp_max", se.getRsvpMax())
                            .append("expire", expire)
                            .append("image", se.getImageUrl())
                            .append("thumbnail", se.getThumbnailUrl())
                            .append("guildId", guildId);

            Main.getDBDriver().getEventCollection().replaceOne(eq("_id", se.getId()), entryDocument);

            // auto-sort
            int sortType = Main.getScheduleManager().getAutoSort(channelId);
            if(sortType == 1)
            {
                Main.getScheduleManager().sortSchedule(channelId, false);
            }
            if(sortType == 2)
            {
                Main.getScheduleManager().sortSchedule(channelId, true);
            }
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
