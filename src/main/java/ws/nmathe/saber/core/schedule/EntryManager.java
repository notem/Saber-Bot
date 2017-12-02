package ws.nmathe.saber.core.schedule;

import com.mongodb.client.MongoIterable;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
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


/**
 * Manages the full listing of entries for all attached guilds
 * Responsible for checking for expired entries, syncing channels to
 * their respective google calendar address, managing IDs, and removing/creating
 * entries.
 */
public class EntryManager
{
    private Random generator;
    public enum type { FILL, EMPTY, UPDATE1, UPDATE2, UPDATE3 }

    /** construct EntryManager and seed random from OS random source */
    public EntryManager()
    {
        this.generator = new Random(new SecureRandom().nextLong()); // not cryptographically secure, which is fine
    }

    /**
     * creates the scheduledExecutor thread pool and starts schedule timers which
     * check for expired entry timers and adjust the message display timer
     */
    public void init()
    {
        /* thread to fill announcement queues and thread to empty announcement queues,
         share the same scheduler to avoid collisions */
        ScheduledExecutorService announcementScheduler = Executors.newSingleThreadScheduledExecutor();
        // fill
        announcementScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.FILL),
                30, 30, TimeUnit.SECONDS);
        // empty
        announcementScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.EMPTY),
                15, 20, TimeUnit.SECONDS);

        // scheduler for threads to adjust entry display timers
        ScheduledExecutorService updateDisplayScheduler = Executors.newSingleThreadScheduledExecutor();
        // 1 day timer
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE3),
                12*60*60, 12*60*60, TimeUnit.SECONDS);
        // 1 hour timer
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE2),
                60*30, 60*30, TimeUnit.SECONDS);
        // 4.5 min timer
        updateDisplayScheduler.scheduleWithFixedDelay(
                new EntryProcessor(type.UPDATE1),
                60*4+30, 60*3, TimeUnit.SECONDS);
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
                if( Main.getScheduleManager().isRSVPEnabled(channelId) )
                {
                    Map<String, String> map = Main.getScheduleManager().getRSVPOptions(channelId);
                    addRSVPReactions(map, Main.getScheduleManager().getRSVPClear(channelId), msg);
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
                                .append("orig_start", se.getRecurrence().getOriginalStart())
                                .append("count", se.getRecurrence().getCount())
                                .append("start_disabled", false)
                                .append("end_disabled", false)
                                .append("reminders_disabled", false)
                                .append("expire", finalExpire)
                                .append("deadline", finalDeadline)
                                .append("guildId", se.getGuildId())
                                .append("location", se.getLocation());

                Main.getDBDriver().getEventCollection().insertOne(entryDocument);

                // auto-sort
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
     */
    public void updateEntry(ScheduleEntry se, boolean sort)
    {
        Message origMessage = se.getMessageObject();
        if(origMessage == null) return;

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

        // generate event display message
        Message message = MessageGenerator.generate(se);

        // update message display
        Date finalExpire = expire;
        Date finalDeadline = deadline;
        MessageUtilities.editMsg(message, origMessage, msg ->
        {
            try
            {
                String guildId = msg.getGuild().getId();
                String channelId = msg.getChannel().getId();

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
                                .append("messageId", msg.getId())
                                .append("channelId", channelId)
                                .append("googleId", se.getGoogleId())
                                .append("rsvp_members", se.getRsvpMembers())
                                .append("rsvp_limits", se.getRsvpLimits())
                                .append("start_disabled", se.isQuietStart())
                                .append("end_disabled", se.isQuietEnd())
                                .append("reminders_disabled", se.isQuietRemind())
                                .append("expire", finalExpire)
                                .append("orig_start", se.getRecurrence().getOriginalStart())
                                .append("count", se.getRecurrence().getCount())
                                .append("image", se.getImageUrl())
                                .append("thumbnail", se.getThumbnailUrl())
                                .append("deadline", finalDeadline)
                                .append("guildId", guildId)
                                .append("announcements", new ArrayList<>(se.getAnnouncements()))
                                .append("announcement_dates", se.getAnnouncementDates())
                                .append("announcement_times", se.getAnnouncementTimes())
                                .append("announcement_messages", se.getAnnouncementMessages())
                                .append("announcement_targets", se.getAnnouncementTargets())
                                .append("location", se.getLocation());

                Main.getDBDriver().getEventCollection().replaceOne(eq("_id", se.getId()), entryDocument);

                // auto-sort
                autoSort(sort, channelId);
            }
            catch(Exception e)
            {
                Logging.exception(EntryManager.class, e);
            }
        });
    }


    /**
     * adds rsvp reactions to a message
     * @param options (Map) mapping of rsvp emojis to rsvp names
     * @param message (Message) discord message object
     */
    public static void addRSVPReactions(Map<String, String> options, String clearEmoji, Message message)
    {
        for(String emoji : options.keySet())
        {
            addRSVPReaction(emoji, message);
        }
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
        if(EmojiManager.isEmoji(emoji))
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
