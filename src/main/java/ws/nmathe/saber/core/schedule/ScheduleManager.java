package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
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
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.set;

/**
 * Manage schedules and their settings for all guilds
 */
public class ScheduleManager
{
    private Set<String> locks = new HashSet<>(); // locks channels from running multiple sorts simultaneously
    private Integer MAX_SIZE_TO_SYNC = 15;  // do not sort schedules more than this number of events

    /**
     * starts a scheduled thread responsible for synchronizing channels with their linked google calendar counterparts
     * init() need not be called if the bot has not been configured to use a google service account
     */
    public void init()
    {   // every 15 minutes create a thread to check for schedules to sync
        ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(1);
        syncScheduler.scheduleAtFixedRate(new ScheduleSyncer(), 30, 30, TimeUnit.MINUTES);
    }

    /**
     * Create a new schedule and it's associated schedule channel, if the bot cannot create the
     * new channel no schedule will be created
     * @param gId (String) guild ID
     * @param optional (String) optional name of schedule channel
     */
    public void createSchedule(String gId, String optional)
    {
        JDA jda = Main.getShardManager().getJDA(gId);

        // bot self permissions
        Collection<Permission> channelPerms = Stream.of(Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_ATTACH_FILES)
                .collect(Collectors.toList());

        // create channel and get ID
        String cId;
        try
        {
            Guild guild = jda.getGuildById(gId);
            cId = guild.getController().createTextChannel(optional!=null ? optional : "new_schedule")
                    .addPermissionOverride(guild.getMember(jda.getSelfUser()),          // allow self permissions
                            channelPerms, new ArrayList<>())
                    .addPermissionOverride(guild.getPublicRole(), new ArrayList<>(),    // disable @everyone message write
                            Collections.singletonList(Permission.MESSAGE_WRITE))
                    .complete().getId();
        }
        catch (PermissionException e)
        {
            String m = e.getMessage() + ": Guild ID " + gId;
            Logging.warn(this.getClass(), m);
            return;
        }
        catch (Exception e)
        {
            Logging.exception(this.getClass(), e);
            return;
        }

        // create the schedule database entry
        createNewSchedule(cId, gId);
    }


    /**
     * Convert a pre-existing discord channel to a new saber schedule channel
     * @param channel (TextChannel) a pre-existing channel to convert to a schedule
     */
    public void createSchedule(TextChannel channel)
    {
        JDA jda = Main.getShardManager().getJDA(channel.getGuild().getId());

        // bot self permissions
        Collection<Permission> channelPerms = Stream.of(Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_ATTACH_FILES)
                .collect(Collectors.toList());

        // attempt to set the channel permissions
        try
        {
            channel.createPermissionOverride(channel.getGuild().getMember(jda.getSelfUser())) // self perms
                    .setAllow(channelPerms).queue();
            channel.createPermissionOverride(channel.getGuild().getPublicRole())              // @everyone perms
                    .setDeny(Collections.singleton(Permission.MESSAGE_WRITE)).queue();
        }
        catch (PermissionException e)
        {
            String m = e.getMessage() + ": Guild ID " + channel.getGuild().getId();
            Logging.warn(this.getClass(), m);
        }
        catch (Exception e)
        {
            Logging.exception(this.getClass(), e);
        }

        // create the schedule database entry
        createNewSchedule(channel.getId(), channel.getGuild().getId());
    }

    /**
     * initializes default values and creates a new database entry to represent the schedule
     * @param channelId the channel (and now schedule) ID; should be a unique Snowflake
     * @param guildId unique snowflake for the guild to which the channel/schedule belongs
     */
    private void createNewSchedule(String channelId, String guildId)
    {
        // default reminders
        List<Integer> default_reminders = new ArrayList<>();
        default_reminders.add(10);

        // default rsvp options
        Map<String, String> default_rsvp = new LinkedHashMap<>();
        default_rsvp.put(Main.getBotSettingsManager().getYesEmoji(), "Yes");
        default_rsvp.put(Main.getBotSettingsManager().getNoEmoji(), "No");
        default_rsvp.put(Main.getBotSettingsManager().getClearEmoji(), "Undecided");

        // create DB entry
        Document schedule =
                new Document("_id", channelId)
                        .append("guildId", guildId)
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
                        .append("sync_address", "off")
                        .append("rsvp_options", default_rsvp);

        Main.getDBDriver().getScheduleCollection().insertOne(schedule);
    }

    /**
     * Removes a schedule and attempts to delete the schedule's channel
     * @param cId (String) ID of channel / schedule (synonymous)
     */
    public void deleteSchedule(String cId)
    {
        // identify which shard is responsible for the schedule
        Document doc = Main.getDBDriver().getScheduleCollection()
                .find(eq("_id", cId))
                .projection(fields(include("guildId")))
                .first();
        JDA jda = Main.getShardManager().getJDA(doc.getString("guildId"));

        try
        {
            jda.getTextChannelById(cId).delete().complete();
        }
        catch(PermissionException e)
        {
            String m = e.getMessage() + ": " + e.getPermission();
            Logging.warn(this.getClass(), m);
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }

        Main.getDBDriver().getEventCollection().deleteMany(eq("channelId", cId));
        Main.getDBDriver().getScheduleCollection().deleteOne(eq("_id", cId));
    }

    /**
     * is a channel an initialized schedule?
     * @param cId (String) channel ID, synonymous to schedule id
     * @return (boolean) true if channel ID maps to a schedule
     */
    public boolean isSchedule(String cId)
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
        if(this.getScheduleSize(cId) > MAX_SIZE_TO_SYNC) return;
        if(this.isLocked(cId)) return;

        this.lock(cId); // lock the channel

        // encapsulate in try block,
        // always unlock the schedule at finish regardless of success or failure
        try
        {
            // identify which shard is responsible for the schedule
            Document doc = Main.getDBDriver().getScheduleCollection()
                    .find(eq("_id", cId))
                    .projection(fields(include("guildId")))
                    .first();
            JDA jda = Main.getShardManager().getJDA(doc.getString("guildId"));

            // find the message channel and send the 'is typing' while processing
            MessageChannel chan = jda.getTextChannelById(cId);
            chan.sendTyping().queue();

            int sortOrder = 1;
            if(reverseOrder)
                sortOrder = -1;

            LinkedList<ScheduleEntry> unsortedEntries = new LinkedList<>();
            Main.getDBDriver().getEventCollection().find(eq("channelId", cId))
                    .sort(new Document("start", sortOrder))
                    .forEach((Consumer<? super Document>) document -> unsortedEntries.add(new ScheduleEntry(document)));

            // selection sort the entries by timestamp
            while (!unsortedEntries.isEmpty())
            {
                chan.sendTyping().queue();   // continue to send 'is typing'

                ScheduleEntry top = unsortedEntries.pop();
                ScheduleEntry min = top;
                for (ScheduleEntry cur : unsortedEntries)
                {
                    Message minMsg = min.getMessageObject();
                    Message topMsg = cur.getMessageObject();
                    if (minMsg!=null && topMsg!=null)
                    {
                        OffsetDateTime a = minMsg.getCreationTime();
                        OffsetDateTime b = topMsg.getCreationTime();
                        if (a.isAfter(b))
                        {
                            min = cur;
                        }
                    }

                }
                // swap messages and update db
                if (!(min==top))
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
        catch(PermissionException e)
        {
            String m = e.getMessage() + ": Channel ID " + cId;
            Logging.warn(this.getClass(), m);
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
        {
            return false;
        }
        return settings.getBoolean("rsvp_enabled", false);
    }

    public boolean isRSVPConfirmationsEnabled(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return false;
        }
        return settings.getBoolean("rsvp_confirmations", false);
    }

    public boolean isEndFormatOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return false;
        }

        String format = (String) settings.get("announcement_format_end");
        return !(format == null);
    }

    public boolean isEndChannelOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return false;
        }

        String format = (String) settings.get("announcement_channel_end");
        return !(format == null);
    }

    public boolean isRemindFormatOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return false;
        }

        String format = (String) settings.get("reminder_format");
        return !(format == null);
    }

    public boolean isRemindChanOverridden(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return false;
        }

        String format = (String) settings.get("reminder_channel");
        return !(format == null);
    }

    public boolean isRSVPExclusive(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        return settings == null || settings.getBoolean("rsvp_exclusivity", true);
    }

    /*
     * Getters
     * Should never return null
     */

    public List<String> getSchedulesForGuild(String gId)
    {
        List<String> list = new ArrayList<>();
        for (Document document : Main.getDBDriver().getScheduleCollection().find(eq("guildId", gId)))
        {
            list.add(document.getString("_id"));
        }
        return list;
    }

    public String getStartAnnounceChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceChan();
        }
        String channel = settings.getString("announcement_channel");
        if(channel == null)
        {
            return Main.getBotSettingsManager().getAnnounceChan();
        }
        return channel;
    }

    public String getStartAnnounceFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceFormat();
        }
        String format = settings.getString("announcement_format");
        if(format == null)
        {
            return Main.getBotSettingsManager().getAnnounceFormat();
        }
        return format;
    }

    public String getEndAnnounceChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceChan();
        }
        String chan = settings.getString("announcement_channel_end");
        if(chan == null)
        {
            return settings.getString("announcement_channel");
        }
        return chan;
    }

    public String getEndAnnounceFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceFormat();
        }
        String format = settings.getString("announcement_format_end");
        if(format == null)
        {
            return settings.getString("announcement_format");
        }
        return format;
    }

    public String getClockFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getClockFormat();
        }
        String clock = settings.getString("clock_format");
        if(clock == null)
        {
            return Main.getBotSettingsManager().getClockFormat();
        }
        return clock;
    }

    public ZoneId getTimeZone(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return ZoneId.of(Main.getBotSettingsManager().getTimeZone());
        }
        ZoneId zone = ZoneId.of(settings.getString("timezone"));
        if(zone == null)
        {
            return ZoneId.of(Main.getBotSettingsManager().getTimeZone());
        }
        return zone;
    }

    public List<ZoneId> getAltZones(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return new ArrayList<>();
        }
        List<String> zones = (List<String>) settings.get("alt_zones");
        if(zones == null)
        {
            return new ArrayList<>();
        }
        return zones.stream().map(ZoneId::of).collect(Collectors.toList());
    }

    public String getAddress(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return "off";
        }

        String address = settings.getString("sync_address");
        if(address == null)
        {
            return "off";
        }
        return address;
    }

    public Date getSyncTime(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                    LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant());
        }

        Date syncTime = settings.getDate("sync_time");
        if(syncTime == null)
        {
            return Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                    LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant());
        }
        return syncTime;
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getReminders(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return new ArrayList<>();
        }

        List<Integer> reminders = (List<Integer>) settings.get("default_reminders");
        if(reminders == null)
        {
            return new ArrayList<>();
        }
        return reminders;
    }

    public String getReminderChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceChan();
        }

        String chan_name = (String) settings.get("reminder_channel");
        if(chan_name == null )
        {
            return (String) settings.get("announcement_channel");
        }
        return chan_name;

    }

    public String getReminderFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettingsManager().getAnnounceFormat();
        }

        String format = (String) settings.get("reminder_format");
        if(format == null )
        {
            return (String) settings.get("announcement_format");
        }
        return format;
    }

    public String getStyle(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return "FULL";
        }

        String style = (String) settings.get("display_style");
        if(style == null)
        {
            return "FULL";
        }
        return style;
    }

    public int getSyncLength(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return 7;
        }

        Integer len = (Integer) settings.get("sync_length");
        if(len == null)
        {
            return 7;
        }
        return len;
    }

    public String getSyncUser(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return null;
        }

        String user = settings.getString("sync_user");
        if(user == null)
        {
            return null;
        }
        return user;
    }

    public int getAutoSort(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return 0;
        }

        Integer sort = (Integer) settings.get("auto_sort");
        if(sort == null)
        {
            return 0;
        }
        return sort;
    }

    public Map<String, String> getRSVPOptions(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return new HashMap<>();
        }

        Map<String, String> map = (Map) settings.get("rsvp_options");
        if(map == null)
        {
            map = new LinkedHashMap<>();
            map.put(Main.getBotSettingsManager().getYesEmoji(), "Yes");
            map.put(Main.getBotSettingsManager().getNoEmoji(), "No");
            map.put(Main.getBotSettingsManager().getClearEmoji(), "Undecided");
        }
        return map;
    }

    public String getRSVPClear(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return "";
        }
        String emoji = settings.getString("rsvp_clear");
        if(emoji == null)
        {
            return "";
        }
        return emoji;
    }

    public String getRSVPLogging(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return "";
        }
        String loggingChannel = settings.getString("rsvp_logging");
        if(loggingChannel == null)
        {
            return "";
        }
        return loggingChannel;
    }

    public List<Integer> getEndReminders(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if(settings == null)
        {
            return new ArrayList<>();
        }
        List<Integer> reminders = (List<Integer>) settings.get("end_reminders");
        if(reminders == null)
        {
            return new ArrayList<>();
        }
        return reminders;
    }

    /*
     * setters
     */

    /**
     * Sets the default announcement target channel for a schedule
     */
    public void setAnnounceChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("announcement_channel", chan));
    }

    /**
     * Sets the default announcement message format for a schedule
     */
    public void setAnnounceFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("announcement_format", format));
    }

    /**
     * Sets the end announcement target channel for a schedule
     */
    public void setEndAnnounceChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("announcement_channel_end", chan));
    }

    /**
     * Sets the end announcement message format for a schedule
     */
    public void setEndAnnounceFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("announcement_format_end", format));
    }

    /**
     * Sets the display format for event times
     */
    public void setClockFormat(String cId, String clock )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("clock_format", clock));
    }

    /**
     * Sets the schedule's timezone
     */
    public void setTimeZone(String cId, ZoneId zone)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("timezone", zone.toString()));
    }

    /**
     * sets alternative zones
     */
    public void setAltZones(String cId, List<ZoneId> zoneIds)
    {
        List<String> zones = zoneIds.stream().map(zoneId -> zoneId.toString()).collect(Collectors.toList());
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("alt_zones"))
        {
            doc.append("alt_zones", zones);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("alt_zones", zones));
        }
    }

    /**
     * Sets the google calendar address to be used for synchronization
     */
    public void setAddress(String cId, String address)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("sync_address", address));
    }

    /**
     * Sets the daily time in which a schedule synced with a google calendar should re-sync
     */
    public void setSyncTime(String cId, Date syncTime)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("sync_time", syncTime));
    }

    /**
     * Sets the time offsets for reminders
     */
    public void setReminders(String cId, List<Integer> reminders)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("default_reminders", reminders));
    }

    /**
     * Sets the time offsets for end reminders
     */
    public void setEndReminders(String cId, List<Integer> reminders)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("end_reminders"))
        {
            doc.append("end_reminders", reminders);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("end_reminders", reminders));
        }
    }

    /**
     * Sets the reminder target channel for a schedule
     */
    public void setReminderChan(String cId, String chan )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("reminder_channel", chan));
    }

    /**
     * Sets the reminder message format for a schedule
     */
    public void setReminderFormat(String cId, String format )
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("reminder_format", format));
    }

    /**
     * Sets whether or not to allow RSVP for events on a schedule
     */
    public void setRSVPEnable(String cId, boolean value)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("rsvp_enabled", value));
    }

    /**
     * Sets the style type to be used by events on a schedule
     * "full"- all information
     * "narrow"- truncated, less information
     */
    public void setStyle(String cId, String style)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("display_style", style));
    }

    /**
     * Sets the number of days a schedule should sync when syncing to a google calendar
     */
    public void setSyncLength(String cId, int len)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("sync_length", len));
    }

    /**
     * Sets if/how a schedule should auto sort events
     * 0- off; 1- asc; 2- desc
     */
    public void setAutoSort(String cId, int type)
    {
        Main.getDBDriver().getScheduleCollection().updateOne(eq("_id",cId), set("auto_sort", type));
    }

    /**
     * Sets the mapping of emoji->rsvp_groups for a schedule
     */
    public void setRSVPOptions(String cId, Map<String, String> options)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("rsvp_options"))
        {
            doc.append("rsvp_options", options);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("rsvp_options", options));
        }
    }

    /**
     * Sets whether or not a schedule should add a 'clear all RSVPs' emoji reaction to events
     */
    public void setRSVPClear(String cId, String emoji)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("rsvp_clear"))
        {
            doc.append("rsvp_clear", emoji);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("rsvp_clear", emoji));
        }
    }

    /**
     * Sets whether or not a schedule should allow users to RSVP for multiple group
     */
    public void setRSVPExclusivity(String cId, Boolean bool)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("rsvp_exclusivity"))
        {
            doc.append("rsvp_exclusivity", bool);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("rsvp_exclusivity", bool));
        }
    }

    /**
     * Sets whether or not a schedule should notify users when they RSVP
     */
    public void setRSVPConfirmations(String cId, Boolean bool)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("rsvp_confirmations"))
        {
            doc.append("rsvp_confirmations", bool);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("rsvp_confirmations", bool));
        }
    }

    /**
     * Sets a schedule's channel to be used for RSVP logging
     */
    public void setRSVPLoggingChannel(String cId, String channelIdentifier)
    {
        Document doc = Main.getDBDriver().getScheduleCollection().find(eq("_id", cId)).first();
        if(!doc.containsKey("rsvp_logging"))
        {
            doc.append("rsvp_logging", channelIdentifier);
            Main.getDBDriver().getScheduleCollection().replaceOne(eq("_id", cId), doc);
        } else
        {
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("rsvp_logging", channelIdentifier));
        }
    }
}
