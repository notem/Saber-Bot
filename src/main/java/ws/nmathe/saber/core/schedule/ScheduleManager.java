package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.exceptions.PermissionException;
import org.bson.Document;
import ws.nmathe.saber.Main;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Manage's the settings for all valid schedule channels
 */
public class ScheduleManager
{
    public void init()
    {
        // every 15 minutes create a thread to check for schedules to sync
        ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(1);
        syncScheduler.scheduleAtFixedRate( new ScheduleSyncer(),
                3, 60*15, TimeUnit.SECONDS );
    }

    public void createSchedule(String gId, String optional)
    {
        String cId;
        try
        {
            cId = Main.getBotJda().getGuildById(gId)
                    .getController().createTextChannel(optional!=null ? optional : "new_schedule").complete().getId();
        }
        catch(PermissionException ignored)
        { return; }

        List<Integer> default_reminders = new ArrayList<Integer>();
        default_reminders.add(10);

        Document schedule =
                new Document("_id", cId)
                        .append("guildId", gId)
                        .append("announcement_channel", Main.getBotSettings().getAnnounceChan())
                        .append("announcement_format", Main.getBotSettings().getAnnounceFormat())
                        .append("clock_format", Main.getBotSettings().getClockFormat())
                        .append("timezone", Main.getBotSettings().getTimeZone())
                        .append("sync_time", Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                                LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant()))
                        .append("default_reminders", default_reminders)
                        .append("sync_address", "off");

        Main.getDBDriver().getScheduleCollection().insertOne(schedule);
    }

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

    public List<String> getSchedulesForGuild(String gId)
    {
        List<String> list = new ArrayList<>();
        for (Document document : Main.getDBDriver().getScheduleCollection().find(eq("guildId", gId)))
        {
            list.add((String) document.get("_id"));
        }
        return list;
    }

    public String getAnnounceChan(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettings().getAnnounceChan();
        }
        return (String) settings.get("announcement_channel");
    }

    public String getAnnounceFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettings().getAnnounceFormat();
        }
        return (String) settings.get("announcement_format");
    }

    public String getClockFormat(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Main.getBotSettings().getClockFormat();
        }
        return (String) settings.get("clock_format");
    }

    public ZoneId getTimeZone(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return ZoneId.of(Main.getBotSettings().getTimeZone());
        }
        return ZoneId.of((String) settings.get("timezone"));
    }

    public String getAddress(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return "off";
        }
        return (String) settings.get("sync_address");
    }

    public Date getSyncTime(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return Date.from(ZonedDateTime.of(LocalDate.now().plusDays(1),
                    LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant());
        }
        return (Date) settings.get("sync_time");
    }

    public List<Integer> getDefaultReminders(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        if( settings == null )
        {
            return new ArrayList<>(10);
        }
        return (List<Integer>) settings.get("default_reminders");
    }

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

    public boolean isASchedule(String cId)
    {
        Document settings = Main.getDBDriver().getScheduleCollection().find(eq("_id",cId)).first();
        return settings != null;
    }
}
