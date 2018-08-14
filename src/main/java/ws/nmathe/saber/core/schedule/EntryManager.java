package ws.nmathe.saber.core.schedule;

import com.mongodb.MongoException;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;


/**
 * Manages the full listing of entries for all attached guilds
 * Responsible for checking for expired entries, syncing channels to
 * their respective google calendar address, managing IDs, and removing/creating
 * entries.
 */
public class EntryManager
{
    private Random generator;
    public enum type { PROCESS, UPDATE1, UPDATE2, UPDATE3 }

    /** construct EntryManager and seed random from OS random source */
    public EntryManager()
    {   // use system random to seed to avoid repeat seed values on bot restart
        this.generator = new Random(new SecureRandom().nextLong());
    }

    /**
     * creates the scheduledExecutor thread pool and starts schedule timers which
     * check for expired entry timers and adjust the message display timer
     */
    public void init()
    {
        /* thread to process events */
        ScheduledExecutorService announcementScheduler = Executors.newSingleThreadScheduledExecutor();
        announcementScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.PROCESS),
                15, 15, TimeUnit.SECONDS);

        // scheduler for threads to adjust entry display timers
        ScheduledExecutorService updateDisplayScheduler = Executors.newSingleThreadScheduledExecutor();
        // updates events with times >24h
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE3),
                12, 6, TimeUnit.HOURS);
        // updates events with times >1 hour (but <24h)
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE2),
                30, 15, TimeUnit.MINUTES);
        // update events with times <1 hour
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE1),
                5, 3, TimeUnit.MINUTES);
    }

    /**
     * Create a new entry on a schedule
     * @param se (ScheduleEntry) the base ScheduleEntry object to use
     */
    public Integer newEntry(ScheduleEntry se, boolean sort)
    {
        // identify which shard is responsible for the schedule
        String guildId = se.getGuildId();
        String channelId = se.getChannelId();
        JDA jda = Main.getShardManager().getJDA(guildId);

        // generate the reminders
        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                .reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));

        // process expiration date
        Date expire = null;
        if (se.getExpire() != null)
        {
            expire = Date.from(se.getExpire().toInstant());
        }

        // process deadline
        Date deadline = null;
        if (se.getDeadline() != null)
        {
            deadline = Date.from(se.getDeadline().toInstant());
        }

        // is rsvp enabled on the channel set empty rsvp lists
        if( Main.getScheduleManager().isRSVPEnabled(se.getChannelId()) )
        {
            for(String type : Main.getScheduleManager().getRSVPOptions(se.getChannelId()).values())
            {
                se.setRsvpMembers(type, new ArrayList<>());
            }
        }

        // generate event display message
        se.setId(this.newId());
        Message message = MessageGenerator.generate(se);

        // send message to schedule
        TextChannel channel = jda.getTextChannelById(channelId);
        Date finalExpire = expire;
        Date finalDeadline = deadline;
        MessageUtilities.sendMsg(message, channel, msg ->
        {
            try
            {
                // add reaction options if rsvp is enabled
                if (Main.getScheduleManager().isRSVPEnabled(channelId))
                {
                    Map<String, String> map = Main.getScheduleManager().getRSVPOptions(channelId);
                    addRSVPReactions(map, Main.getScheduleManager().getRSVPClear(channelId), msg, se);
                }

                // add new document
                Document entryDocument =
                        new Document("_id", se.getId())
                                .append("title", se.getTitle())
                                .append("start", Date.from(se.getStart().toInstant()))
                                .append("end", Date.from(se.getEnd().toInstant()))
                                .append("comments", se.getComments())
                                .append("recurrence", se.getRepeat())
                                .append("reminders", se.getReminders())
                                .append("end_reminders", se.getEndReminders())
                                .append("url", se.getTitleUrl())
                                .append("hasStarted", se.hasStarted())
                                .append("messageId", msg.getId())
                                .append("channelId", se.getChannelId())
                                .append("googleId", se.getGoogleId())
                                .append("rsvp_members", se.getRsvpMembers())
                                .append("rsvp_limits", se.getRsvpLimits())
                                .append("image", se.getImageUrl())
                                .append("thumbnail", se.getThumbnailUrl())
                                .append("orig_start", Date.from(se.getRecurrence().getOriginalStart().toInstant()))
                                .append("count", se.getRecurrence().getCount())
                                .append("start_disabled", false)
                                .append("end_disabled", false)
                                .append("reminders_disabled", false)
                                .append("expire", finalExpire)
                                .append("deadline", finalDeadline)
                                .append("guildId", se.getGuildId())
                                .append("location", se.getLocation())
                                .append("description", se.getDescription())
                                .append("color", se.getColor());

                Main.getDBDriver().getEventCollection().insertOne(entryDocument);

                // auto-sort the schedule if configured
                autoSort(sort, channelId);
            }
            catch(Exception e)
            {
                Logging.exception(EntryManager.class, e);
            }
        });

        return se.getId();
    }

    /**
     * Update an entry with a new configuration
     * All schedule entry parameters should be filled.
     * The ID of the ScheduleEntry must not have been changed.
     * @param se (ScheduleEntry) the new schedule entry object
     * @return true if successful, otherwise false
     */
    public boolean updateEntry(ScheduleEntry se, boolean sort)
    {
        // process expiration date
        Date expire = null;
        if (se.getExpire() != null)
        {
            expire = Date.from(se.getExpire().toInstant());
        }

        // process deadline
        Date deadline = null;
        if (se.getDeadline() != null)
        {
            deadline = Date.from(se.getDeadline().toInstant());
        }

        // update message display
        Date finalExpire = expire;
        Date finalDeadline = deadline;

        try
        {
            // replace whole document
            Document entryDocument =
                    new Document("_id", se.getId())
                            .append("title", se.getTitle())
                            .append("start", Date.from(se.getStart().toInstant()))
                            .append("end", Date.from(se.getEnd().toInstant()))
                            .append("comments", se.getComments())
                            .append("recurrence", se.getRepeat())
                            .append("reminders", se.getReminders())
                            .append("end_reminders", se.getEndReminders())
                            .append("url", se.getTitleUrl())
                            .append("hasStarted", se.hasStarted())
                            .append("messageId", se.getMessageId())
                            .append("channelId", se.getChannelId())
                            .append("googleId", se.getGoogleId())
                            .append("rsvp_members", se.getRsvpMembers())
                            .append("rsvp_limits", se.getRsvpLimits())
                            .append("start_disabled", se.isQuietStart())
                            .append("end_disabled", se.isQuietEnd())
                            .append("reminders_disabled", se.isQuietRemind())
                            .append("expire", finalExpire)
                            .append("orig_start", Date.from(se.getRecurrence().getOriginalStart().toInstant()))
                            .append("count", se.getRecurrence().getCount())
                            .append("image", se.getImageUrl())
                            .append("thumbnail", se.getThumbnailUrl())
                            .append("deadline", finalDeadline)
                            .append("guildId", se.getGuildId())
                            .append("announcements", new ArrayList<>(se.getAnnouncements()))
                            .append("announcement_dates", se.getAnnouncementDates())
                            .append("announcement_times", se.getAnnouncementTimes())
                            .append("announcement_messages", se.getAnnouncementMessages())
                            .append("announcement_targets", se.getAnnouncementTargets())
                            .append("location", se.getLocation())
                            .append("description", se.getDescription())
                            .append("color", se.getColor());

            UpdateResult res = Main.getDBDriver().getEventCollection()
                    .replaceOne(eq("_id", se.getId()), entryDocument);
            if (!res.wasAcknowledged())
            {
                Logging.warn(this.getClass(), "Attempt to update '"+se.getTitle()+"' was unacknowledged!");
                return false; // return false, might result in skipped announcement or other issues
            }

            // update the event message with the information changes (if any)
            // this may (is) over-aggressive, however it is convenient and easier to manage
            // (ie. avoid updating the display in other code sections)
            se.reloadDisplay();

            // auto-sort the schedule if configured
            // may be necessary if the start time was changed
            autoSort(sort, se.getChannelId());
            return true;
        }
        catch(Exception e)
        {
            Logging.exception(EntryManager.class, e);
            return false;
        }
    }

    /**
     * update the event's database entry's hasStarted flag to true
     * @param se schedule entry which has started
     * @return true if successful, otherwise false
     */
    public boolean startEvent(ScheduleEntry se)
    {
        try
        {
            UpdateResult res = Main.getDBDriver().getEventCollection()
                    // using the 'update many' call seems to work more effectively
                    .updateMany(eq("_id", se.getId()), set("hasStarted", true));
            if (!res.wasAcknowledged())
            {
                Logging.warn(this.getClass(), "Attempt to update '"+se.getTitle()+"' was unacknowledged!");
                return false; // might result in skipped announcements or other issues
            }
            se.reloadDisplay();
            return true;
        }
        catch(MongoException e)
        {
            Logging.exception(this.getClass(), e);
            return false;
        }
    }

    /**
     * adds rsvp reactions to a message
     * @param options (Map) mapping of rsvp emojis to rsvp names
     * @param clearEmoji unicode emoji to use for the clear action (empty if clear is disabled)
     * @param message (Message) discord message object
     * @param se the schedule entry object
     */
    public static void addRSVPReactions
    (Map<String, String> options, String clearEmoji, Message message, ScheduleEntry se)
    {   // add all RSVP emoji's
        for(String emoji : options.keySet())
        {   // don't add the reaction for categories with 0 limit
            if (se.getRsvpLimit(options.get(emoji)) != 0)
                addRSVPReaction(emoji, message);
        }
        // add clear emoji if configured
        if(!clearEmoji.isEmpty())
        {
            addRSVPReaction(clearEmoji, message);
        }
    }

    /**
     * helper to addRSVPReactions(..)
     * @param emoji string emoticon, or emote ID
     * @param message message object to react to
     */
    private static void addRSVPReaction(String emoji, Message message)
    {
        if (EmojiManager.isEmoji(emoji))
        {
            message.addReaction(emoji).queue();
        }
        else
        {
            Emote emote;
            for(JDA shard : Main.getShardManager().getShards())
            {
                emote = shard.getEmoteById(emoji);
                if(emote != null)
                {
                    message.addReaction(emote).queue();
                    break;
                }
            }
        }
    }


    /**
     * Handles automatic sorting of a channel
     * @param sort (boolean) should the channel be sorted?
     * @param channelId (String) ID of the channel to sort
     */
    public static void autoSort(boolean sort, String channelId)
    {
        if(sort)
        {
            int sortType = Main.getScheduleManager().getAutoSort(channelId);
            if(sortType == 1)
            {
                Main.getScheduleManager().sortSchedule(channelId, false);
            }
            if(sortType == 2)
            {
                Main.getScheduleManager().sortSchedule(channelId, true);
            }
        }
    }

    /**
     * removes an entry by id from the db
     * @param entryId (Integer) ID of event entry
     * @return true if the remove was acknowledged (safe), otherwise false
     */
    public boolean removeEntry(Integer entryId)
    {
        DeleteResult res = Main.getDBDriver().getEventCollection()
                .deleteMany(eq("_id", entryId));
        return res.wasAcknowledged();
    }

    /**
     * regenerates the displayed Message text for a schedule entry
     * @param eId integer Id
     */
    public void reloadEntry(Integer eId)
    {
        ScheduleEntry se = getEntry(eId);
        if(se == null) return;
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
        Bson document = Main.getDBDriver().getEventCollection().find(eq("_id", ID)).first();
        while (document != null)
        {
            ID = (int) Math.ceil(generator.nextDouble() * (Math.pow(2, 32) - 1));
            document = Main.getDBDriver().getEventCollection().find(eq("_id", ID)).first();
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
        {
            return new ScheduleEntry(entryDocument);
        }
        else    // otherwise return null
        {
            return null;
        }
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
        {
            return new ScheduleEntry(entryDocument);
        }
        else    // otherwise return null
        {
            return null;
        }
    }

    /**
     * Retrieves all entries associated with a guild
     * @param guildId snowflake ID of guild
     * @return all active entries
     */
    public Collection<ScheduleEntry> getEntriesFromGuild(String guildId)
    {
        MongoIterable<ScheduleEntry> entries = Main.getDBDriver().getEventCollection()
                .find(eq("guildId", guildId)).map(ScheduleEntry::new);
        return entries.into(new ArrayList<>());
    }

    /**
     * Retrieves all entries on a channel
     * @param channelId snowflake ID of channel
     * @return all active entries
     */
    public Collection<ScheduleEntry> getEntriesFromChannel(String channelId)
    {
        MongoIterable<ScheduleEntry> entries = Main.getDBDriver().getEventCollection()
                .find(eq("channelId", channelId)).map(ScheduleEntry::new);
        return entries.into(new ArrayList<>());
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
