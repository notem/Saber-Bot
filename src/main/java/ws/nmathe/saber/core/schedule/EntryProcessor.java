package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

/**
 * Used by the Main scheduler timer, a new thread is executed every minute/5minutes/1hour/1day.
 * processes all entries referenced in the collection passed in, checking the start/end time and updating the
 * "time until" display timers.
 * a thread is spawned for each event operation to avoid one problematic event hanging-up the class
 */
class EntryProcessor implements Runnable
{
    // thread pool for level 0 processing
    private static ExecutorService primaryExecutor = Executors.newCachedThreadPool();   // handles fine check tasks
    private static ExecutorService secondaryExecutor = Executors.newCachedThreadPool(); // handles the coarse check tasks

    private int level;
    EntryProcessor(int level)
    {
        this.level = level;
    }

    @SuppressWarnings("unchecked")
    public void run()
    {
        try
        {
            if( level == 0 )    // minute check
            {
                Logging.info(this.getClass(), "Started processing entries at level 0. . .");

                // process entries which are ending
                Bson query = and(eq("hasStarted",true), lte("end", new Date()));

                Main.getDBDriver().getEventCollection().find(query)
                        .forEach((Consumer<? super Document>) document ->
                        {
                            // identify which shard is responsible for the schedule
                            String guildId = document.getString("guildId");
                            JDA jda = Main.getShardManager().getJDA(guildId);

                            // if the shard is not connected, do process the event
                            if(jda == null) return;
                            if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                            primaryExecutor.execute(() ->
                            {
                                // convert to scheduleEntry object and start
                                try
                                {
                                    (new ScheduleEntry(document)).end();
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });

                // process entries which are starting
                query = and(eq("hasStarted",false), lte("start", new Date()));
                Main.getDBDriver().getEventCollection().find(query)
                        .forEach((Consumer<? super Document>) document ->
                        {
                            // identify which shard is responsible for the schedule
                            String guildId = document.getString("guildId");
                            JDA jda = Main.getShardManager().getJDA(guildId);

                            // if the shard is not connected, do process the event
                            if(jda == null) return;
                            if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                            primaryExecutor.execute(() ->
                            {
                                try
                                {    // convert to a POJO and start
                                    (new ScheduleEntry(document)).start();

                                    // if the entry isn't the special exception, update the db entry as started
                                    if(!document.getDate("start").equals(document.getDate("end")))
                                    {
                                        Main.getDBDriver().getEventCollection()
                                                .updateOne(eq("_id", document.get("_id")), set("hasStarted", true));
                                    }
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });

                // process entries with reminders
                query = and(eq("hasStarted",false), lte("reminders", new Date()));

                Main.getDBDriver().getEventCollection().find(query)
                        .forEach((Consumer<? super Document>) document ->
                        {
                            // identify which shard is responsible for the schedule
                            String guildId = document.getString("guildId");
                            JDA jda = Main.getShardManager().getJDA(guildId);

                            // if the shard is not connected, do process the event
                            if(jda == null) return;
                            if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                            primaryExecutor.execute(() ->
                            {
                                try
                                {
                                    // convert to POJO and send a remind
                                    (new ScheduleEntry(document)).remind();

                                    // remove expired reminders
                                    List<Date> reminders = (List<Date>) document.get("reminders");
                                    reminders.removeIf(date -> date.before(new Date()));

                                    // update document
                                    Main.getDBDriver().getEventCollection()
                                            .updateOne(
                                                    eq("_id", document.get("_id")),
                                                    set("reminders", reminders));

                                    (new ScheduleEntry(document)).reloadDisplay();
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });

                Logging.info(this.getClass(), "Finished processing entries at level 0. . .");
            }
            else
            {
                Bson query = new Document(); // should an invalid level ever be passed in, all entries will be reloaded!

                if(level == 1)   // few minute check
                {
                    Logging.info(this.getClass(), "Processing entries at level 1. . .");

                    // adjust timers for entries starting/ending within the next hour
                    query = or(
                            and(
                                    eq("hasStarted",false),
                                    and(
                                            lte("start", Date.from(ZonedDateTime.now().plusHours(1).toInstant())),
                                            gte("start", Date.from(ZonedDateTime.now().plusMinutes(4).toInstant()))
                                    )
                            ),
                            and(
                                    eq("hasStarted", true),
                                    and(
                                            lte("end", Date.from(ZonedDateTime.now().plusHours(1).toInstant())),
                                            gte("end", Date.from(ZonedDateTime.now().plusMinutes(4).toInstant()))
                                    )
                            )
                    );

                }
                else if(level == 2)
                {
                    Logging.info(this.getClass(), "Processing entries at level 2. . .");

                    // purge expiring events
                    query = lte("expire", new Date());

                    //delete message objects
                    Main.getDBDriver().getEventCollection().find(query).forEach((Consumer<? super Document>) document ->
                    {
                        MessageUtilities.deleteMsg((new ScheduleEntry(document)).getMessageObject(), null);
                    });

                    // bulk delete entries from the database
                    Main.getDBDriver().getEventCollection().deleteMany(query);

                    // adjust timers
                    query = or(and(
                            eq("hasStarted",false),
                            and(
                                    lte("start", Date.from(ZonedDateTime.now().plusDays(1).toInstant())),
                                    gte("start", Date.from(ZonedDateTime.now().plusHours(1).toInstant()))
                            )),
                            and(
                                    eq("hasStarted", true),
                                    and(
                                            lte("end", Date.from(ZonedDateTime.now().plusDays(1).toInstant())),
                                            gte("end", Date.from(ZonedDateTime.now().plusHours(1).toInstant()))
                                    )));

                }
                else if(level == 3)
                {
                    Logging.info(this.getClass(), "Processing entries at level 3. . .");

                    // adjust timers for entries that aren't starting/ending within the next day
                    query = or(
                            and(
                                    eq("hasStarted", false),
                                    gte("start", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))),
                            and(
                                    eq("hasStarted", true),
                                    gte("end", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))));

                }

                // reload entries based on the appropriate query
                Main.getDBDriver().getEventCollection().find(query)
                        .forEach((Consumer<? super Document>) document ->
                        {
                            // identify which shard is responsible for the schedule
                            String guildId = document.getString("guildId");
                            JDA jda = Main.getShardManager().getJDA(guildId);

                            // if the shard is not connected, do process the event
                            if(jda == null) return;
                            if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                            secondaryExecutor.execute(() ->
                            {
                                try
                                {   // convert to scheduleEntry object and start
                                    (new ScheduleEntry(document)).reloadDisplay();
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });

                Logging.info(this.getClass(), "Finished processing entries. . .");
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
