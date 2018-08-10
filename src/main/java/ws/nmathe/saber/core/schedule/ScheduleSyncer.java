package ws.nmathe.saber.core.schedule;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.core.settings.GuildSettingsManager;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.set;
import static ws.nmathe.saber.Main.getGuildSettingsManager;

/**
 * Thread used to resync schedules once a day if that schedule
 * is configured to sync to a google calendar address.
 * If the sync fails, the schedule's sync configuration is reset
 */
class ScheduleSyncer implements Runnable
{
    // thread pool for sync jobs
    private static ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("ScheduleSync-%d").build());

    public void run()
    {
        Logging.info(this.getClass(), "Running schedule syncer. . .");
        Bson query = and(
                        ne("sync_address", "off"),
                        lte("sync_time", new Date()));

        Main.getDBDriver().getScheduleCollection()
                .find(query)
                .projection(fields(include("_id", "sync_time", "sync_address","sync_user", "guildId")))
                .forEach((Consumer<? super Document>) document ->
        {
            executor.execute(() ->
            {
                try
                {
                    // identify which shard is responsible for the schedule
                    String guildId = document.getString("guildId");
                    JDA jda = Main.getShardManager().getJDA(guildId);

                    // if the shard is not connected, do not sync schedules
                    if(jda == null) return;
                    if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                    String scheduleId = document.getString("_id");

                    // add one day to sync_time
                    Date syncTime = Date.from(ZonedDateTime.ofInstant(document.getDate("sync_time").toInstant(),
                            Main.getScheduleManager().getTimeZone(scheduleId)).plusDays(1).toInstant());

                    // update schedule document with next sync time
                    Main.getDBDriver().getScheduleCollection()
                            .updateOne(eq("_id", scheduleId), set("sync_time", syncTime));

                    // get the sync address and google credentials, then create the calendar service
                    String address = document.getString("sync_address");
                    Credential credential = document.get("sync_user")==null ? GoogleAuth.authorize() :
                            GoogleAuth.getCredential(document.getString("sync_user"));
                    Calendar service = GoogleAuth.getCalendarService(credential);

                    TextChannel channel = jda.getTextChannelById(document.getString("_id"));
                    if(channel == null) return;

                    // attempt to sync schedule
                    if(Main.getCalendarConverter().checkValidAddress(address, service))
                    {
                        Main.getCalendarConverter().importCalendar(address, channel, service);
                        Logging.info(this.getClass(), "Synchronized schedule #" + channel.getName() + " [" +
                                document.getString("_id") + "] on '" + channel.getGuild().getName() + "' [" +
                                channel.getGuild().getId() + "]");
                    }
                    else    // if sync address is not valid, set it to off
                    {
                        GuildSettingsManager.GuildSettings gs = Main.getGuildSettingsManager().getGuildSettings(guildId);
                        TextChannel control = Main.getShardManager().getJDA(guildId).getTextChannelById(gs.getCommandChannelId());
                        String content = "**Warning:** I failed to auto-sync <#" + scheduleId + "> to *" + address + "*!\n" +
                                "Please make sure that the calendar address is still correct and that the calendar privacy settings have not changed!";
                        MessageUtilities.sendMsg(content, control, null);

                        Logging.warn(this.getClass(), "Failed to synchronize schedule #" + channel.getName() + " [" +
                                document.getString("_id") + "] on '" + channel.getGuild().getName() + "' [" +
                                channel.getGuild().getId() + "]");
                    }
                }
                catch(Exception e)
                {
                    Logging.exception(this.getClass(), e);
                }
            });
        });
    }
}
