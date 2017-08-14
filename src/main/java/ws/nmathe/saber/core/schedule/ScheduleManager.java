package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Manage schedules and their settings for all guilds
 */
public class ScheduleManager
{
    private Set<String> locks = new HashSet<>(); // locks channels from running multiple sorts simultaneously

    public void initScheduleSync()
    {
        // every 15 minutes create a thread to check for schedules to sync
        ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(1);
        syncScheduler.scheduleAtFixedRate( new ScheduleSyncer(), 60*15, 60*15, TimeUnit.SECONDS );
    }

    /**
     * Create a new schedule and it's associated schedule channel, if the bot cannot create the
     * new channel no schedule will be created
     * @param gId (String) guild ID
     * @param optional (String) optional name of schedule channel
     */
    public void createSchedule(String gId, String optional)
    {
        Collection<Permission> channelPerms = Stream.of(Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_ATTACH_FILES)
                .collect(Collectors.toList());
        String cId;
        try
        {
            Guild guild = Main.getBotJda().getGuildById(gId);
            cId = guild.getController().createTextChannel(optional!=null ? optional : "new_schedule")
                    .addPermissionOverride(guild.getMember(Main.getBotJda().getSelfUser()),
                            channelPerms, new ArrayList<>()).complete().getId();
        }
        catch(PermissionException ignored)
        { return; }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
            return;
        }

        List<Integer> default_reminders = new ArrayList<>();
        default_reminders.add(10);

        Document schedule =
                new Document("_id", cId)
                        .append("guildId", gId)
                        .append("announcement_channel", Main.getBotSettingsManager().getAnnounceChan())
                        .append("announcement_format", Main.getBotSettingsManager().getAnnounceFormat())
                        .append("clock_format", Main.getBotSettingsManager().getClockFormat())
                        .append("timezone", Main.getBotSettingsManager().getTimeZone())
                        .append("sync_time", Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                                LocalTime.now().truncatedTo(ChronoUnit.HOURS), ZoneId.systemDefault()).toInstant()))
                        .append("default_reminders", default_reminders)
                        .append("rsvp_enabled", false)
                        .append("display_style", "full")
                        .append("sync_length", 7)
                        .append("sync_address", "off");

        Main.getDBDriver().getScheduleCollection().insertOne(schedule);
    }


    /**
     * Convert a pre-existing discord channel to a new saber schedule channel
     * @param channel (TextChannel) a pre-existing channel to convert to a schedule
     */
    public void createSchedule(TextChannel channel)
    {
        // attempt to set the channel permissions
        Collection<Permission> channelPerms = Stream.of(Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_ATTACH_FILES)
                .collect(Collectors.toList());
        try
        {
            channel.createPermissionOverride(channel.getGuild().getMember(Main.getBotJda().getSelfUser()))
                    .setAllow(channelPerms);
        }
        catch(PermissionException ignored)
        {} // if Saber does not have the permissions, continue on. . .

        List<Integer> default_reminders = new ArrayList<>();
        default_reminders.add(10);

        Document schedule =
                new Document("_id", channel.getId())
                        .append("guildId", channel.getGuild().getId())
                        .append("announcement_channel", Main.getBotSettingsManager().getAnnounceChan())
                        .append("announcement_format", Main.getBotSettingsManager().getAnnounceFormat())
                        .append("clock_format", Main.getBotSettingsManager().getClockFormat())
                        .append("timezone", Main.getBotSettingsManager().getTimeZone())
                        .append("sync_time", Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                                LocalTime.now().truncatedTo(ChronoUnit.HOURS), ZoneId.systemDefault()).toInstant()))
                        .append("default_reminders", default_reminders)
                        .append("rsvp_enabled", false)
                        .append("display_style", "full")
                        .append("sync_length", 7)
                        .append("auto_sort", 0)
                        .append("sync_address", "off");

        Main.getDBDriver().getScheduleCollection().insertOne(schedule);
    }

    /**
     * Removes a schedule and attempts to delete the schedule's channel
     * @param cId (String) ID of channel / schedule (synonymous)
     */
    public void deleteSchedule(String cId)
    {
        try
        {
            Main.getBotJda().getTextChannelById(cId).delete().complete();
        }
        catch(Exception ignored)
        { }

        Main.getDBDriver().getEventCollection().deleteMany(eq("channelId", cId));
        Main.getDBDriver().getScheduleCollection().deleteOne(eq("_id", cId));
    }

    /**
     * is a channel an initialized schedule?
     * @param cId (String) channel ID, synonymous to schedule id
     * @return (boolean) true if channel ID maps to a schedule
     */
    public boolean isASchedule(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        return settings != null;
    }

    /**
     * has a guild reached it's maximum schedule limit?
     * @param gId (String) guild ID
     * @return (boolean) true if guild has reached bot limit on schedule number
     */
    public boolean isLimitReached(String gId)
    {
        long count = Main.getDBDriver().getScheduleCollection().count(eq("guildId",gId));
        return Main.getBotSettingsManager().getMaxSchedules() < count;
    }

    private long getScheduleSize(String cId)
    {
        return Main.getDBDriver().getScheduleCollection().count(eq("channelId",cId));
    }

    /**
     * check to see if the channel is locked
     * @param cId (String) channel ID
     * @return (boolean) true if the channel is locked for sorting
     */
    public boolean isLocked(String cId)
    {
        return this.locks.contains(cId);
    }

    /**
     * locks a schedule (user cannot add/edit new events)
     * @param cId (String) channel ID
     */
    public void lock(String cId)
    {
        this.locks.add(cId); // lock the channel
    }

    /**
     * unlocks a schedule (user is free to add/edit events)
     * @param cId (String) channel ID
     */
    public void unlock(String cId)
    {
        this.locks.remove(cId); // unlock the channel
    }

    // band-aid
    public void clearLocks()
    {
        this.locks = new HashSet<>();
    }

    /**
     * Reorders the schedule so that entries are displayed by start datetime ascending order in
     * the discord schedule channel
     * @param cId schedule ID
     * @param reverseOrder (boolean) whether or not to reverse the sort order
     */
    public void sortSchedule(String cId, boolean reverseOrder)
    {
        if(this.getScheduleSize(cId) > 15)
            return;
        if(this.isLocked(cId))
            return;

        this.lock(cId); // lock the channel

        // encapsulate in try block,
        // always unlock the schedule at finish regardless of success or failure
        try
        {
            // find the message channel and send the 'is typing' while processing
            MessageChannel chan = Main.getBotJda().getTextChannelById(cId);
            chan.sendTyping().queue();

            int sortOrder = 1;
            if(reverseOrder)
                sortOrder = -1;

            LinkedList<ScheduleEntry> unsortedEntries = new LinkedList<>();
            Main.getDBDriver().getEventCollection().find(eq("channelId", cId)).sort(new Document("start", sortOrder))
                    .forEach((Consumer<? super Document>) document -> unsortedEntries.add(new ScheduleEntry(document)));

            // selection sort the entries by timestamp
            while (!unsortedEntries.isEmpty())
            {
                chan.sendTyping().queue();   // continue to send 'is typing'

                ScheduleEntry top = unsortedEntries.pop();
                ScheduleEntry min = top;
                for (ScheduleEntry cur : unsortedEntries)
                {
                    if(min.getMessageObject()==null || cur.getMessageObject()==null)
                    {
                        continue;
                    }

                    OffsetDateTime a = min.getMessageObject().getCreationTime();
                    OffsetDateTime b = cur.getMessageObject().getCreationTime();
                    if (a.isAfter(b))
                    {
                        min = cur;
                    }
                }
                // swap messages and update db
                if(!(min==top))
                {
                    Message tmp = top.getMessageObject();
                    top.setMessageObject(min.getMessageObject());
                    Main.getDBDriver().getEventCollection().updateOne(
                            eq("_id", top.getId()),
                            new Document("$set", new Document("messageId", min.getMessageObject().getId())));

                    min.setMessageObject(tmp);
                    Main.getDBDriver().getEventCollection().updateOne(
                            eq("_id", min.getId()),
                            new Document("$set", new Document("messageId", tmp.getId())));
                }

                // reload display
                top.reloadDisplay();
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
        finally
        {
            this.unlock(cId); // always unlock
        }
    }


    /*
     *
     * Getters and Setters
     *
     */

    public boolean isRSVPEnabled(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return false;
        Object obj = settings.get("rsvp_enabled");
        if(obj == null)
            return false;
        return (Boolean) obj;
    }

    public boolean isEndFormatOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return false;

        String format = (String) settings.get("announcement_format_end");
        return !(format == null);
    }

    public boolean isEndChannelOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return false;

        String format = (String) settings.get("announcement_channel_end");
        return !(format == null);
    }

    public boolean isRemindFormatOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return false;

        String format = (String) settings.get("reminder_format");
        return !(format == null);
    }

    public boolean isRemindChanOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return false;

        String format = (String) settings.get("reminder_channel");
        return !(format == null);
    }

    /*
     *
     */

    public List<String> getSchedulesForGuild(String gId)
    {
        List<String> list = new ArrayList<>();
        for (Document document : Main.getDBDriver().getScheduleCollection().find(eq("guildId", gId)))
            list.add((String) document.get("_id"));
        return list;
    }

    public String getStartAnnounceChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceChan();
        return (String) settings.get("announcement_channel");
    }

    public String getStartAnnounceFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceFormat();
        return (String) settings.get("announcement_format");
    }

    public String getEndAnnounceChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceChan();
        String chan = (String) settings.get("announcement_channel_end");
        if(chan == null)
            return (String) settings.get("announcement_channel");
        else
            return chan;
    }

    public String getEndAnnounceFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceFormat();
        String format = (String) settings.get("announcement_format_end");
        if(format == null)
            return (String) settings.get("announcement_format");
        else
            return format;
    }

    public String getClockFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getClockFormat();
        return (String) settings.get("clock_format");
    }

    public ZoneId getTimeZone(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return ZoneId.of(Main.getBotSettingsManager().getTimeZone());
        return ZoneId.of((String) settings.get("timezone"));
    }

    public String getAddress(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return "off";
        return (String) settings.get("sync_address");
    }

    public Date getSyncTime(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                    LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant());
        return (Date) settings.get("sync_time");
    }

    public List<Integer> getDefaultReminders(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return new ArrayList<>(10);
        return (List<Integer>) settings.get("default_reminders");
    }

    public String getReminderChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceChan();

        String chan_name = (String) settings.get("reminder_channel");
        if(chan_name == null )
            return (String) settings.get("announcement_channel");
        else
            return chan_name;

    }

    public String getReminderFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
            return Main.getBotSettingsManager().getAnnounceFormat();

        String format = (String) settings.get("reminder_format");
        if(format == null )
            return (String) settings.get("announcement_format");
        else
            return format;
    }

    public String getStyle(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
            return "FULL";

        String style = (String) settings.get("display_style");
        if(style == null)
            return "FULL";
        else
            return style;
    }

    public int getSyncLength(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
            return 7;

        Integer len = (Integer) settings.get("sync_length");
        if(len == null)
            return 7;
        else
            return len;
    }

    public int getAutoSort(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
            return 0;

        Integer sort = (Integer) settings.get("auto_sort");
        if(sort == null)
            return 0;
        else
            return sort;
    }

    /*
     *
     */

    public void setAnnounceChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("announcement_channel", chan));
    }

    public void setAnnounceFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("announcement_format", format));
    }

    public void setEndAnnounceChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("announcement_channel_end", chan));
    }

    public void setEndAnnounceFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("announcement_format_end", format));
    }

    public void setClockFormat(String cId, String clock )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("clock_format", clock));
    }

    public void setTimeZone(String cId, ZoneId zone )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("timezone", zone.toString()));
    }

    public void setAddress(String cId, String address)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("sync_address", address));
    }

    public void setSyncTime(String cId, Date syncTime)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("sync_time", syncTime));
    }

    public void setDefaultReminders(String cId, List<Integer> reminders)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("default_reminders", reminders));
    }

    public void setReminderChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("reminder_channel", chan));
    }

    public void setReminderFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("reminder_format", format));
    }

    public void setRSVPEnable(String cId, boolean value)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("rsvp_enabled", value));
    }

    public void setStyle(String cId, String style)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("display_style", style));
    }

    public void setSyncLength(String cId, int len)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("sync_length", len));
    }

    public void setAutoSort(String cId, int type)
    {
        Main.getDBDriver().getScheduleCollection()
                .updateOne(eq("_id",cId), set("auto_sort", type));
    }
}
