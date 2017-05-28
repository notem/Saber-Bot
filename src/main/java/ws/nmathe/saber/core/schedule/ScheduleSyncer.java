package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

/**
 * Thread used to resync schedules once a day if that schedule
 * is configured to sync to a google calendar address.
 * If the sync fails, the schedule's sync configuration is reset
 */
class ScheduleSyncer implements Runnable
{
    public void run()
    {
        Logging.info(this.getClass(), "Running schedule syncer. . .");
        Main.getDBDriver().getScheduleCollection()
                .find(and(
                        ne("sync_address", "off"),
                        lte("sync_time", new Date())))
                .forEach((Consumer<? super Document>) document ->
        {
            String scheduleId = (String) document.get("_id");

            // add one day to sync_time
            Date syncTime = Date.from(ZonedDateTime.ofInstant(document.getDate("sync_time").toInstant(),
                    Main.getScheduleManager().getTimeZone(scheduleId)).plusDays(1).toInstant());

            // update schedule document with next sync time
            Main.getDBDriver().getScheduleCollection()
                    .updateOne(eq("_id", scheduleId), set("sync_time", syncTime));

            // attempt to sync schedule
            if(Main.getCalendarConverter().checkValidAddress(document.getString("sync_address")))
            {
                Main.getCalendarConverter().syncCalendar(
                        (String) document.get("sync_address"),
                        Main.getBotJda().getTextChannelById(document.getString("_id")));

                TextChannel channel = Main.getBotJda().getTextChannelById(document.getString("_id"));
                Logging.info(this.getClass(), "Synchronized schedule #" + channel.getName() + " [" +
                        document.getString("_id") + "] on '" + channel.getGuild().getName() + "' [" +
                        channel.getGuild().getId() + "]");
            }
            else    // if sync address is not valid, set it to off
            {
                Main.getDBDriver().getScheduleCollection()
                        .updateOne(eq("_id", scheduleId), set("sync_address", "off"));
            }
        });
    }
}
