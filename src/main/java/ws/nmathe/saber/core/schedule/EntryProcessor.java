package ws.nmathe.saber.core.schedule;

import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    /// when the database is overwhelmed with write requests (or otherwise just misbehaving)
    /// this hashmap (hashset) protects against announcing events multiple times
    /// event IDs in this hashmap should not be processed when starting/ending/reminding
    private static ConcurrentHashMap<Integer, Object> eventsToBeWritten = new ConcurrentHashMap<>();

    private int level;
    EntryProcessor(int level)
    {
        this.level = level;
    }

    public void run()
    {
        if( level == 0 )    // minute check
        {
            Logging.info(this.getClass(), "Processing entries at level 0. . .");

            // process entries which are ending
            Bson query = and(eq("hasStarted",true), lte("end", new Date()));
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
                            primaryExecutor.execute(() -> {
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
                        }
                    });

            query = and(eq("hasStarted",false), lte("start", new Date()));
            // process entries which are starting
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
                            primaryExecutor.execute(() -> {
                                try
                                {   // if the entry isn't the special exception, update the db entry as started
                                    if(document.getDate("start").equals(document.getDate("end")))
                                    {
                                        // convert to scheduleEntry object and start
                                        (new ScheduleEntry(document)).end();
                                    }
                                    else
                                    {
                                        // convert to a POJO and start
                                        (new ScheduleEntry(document)).start();

                                        eventsToBeWritten.put(document.getInteger("_id"), new Object());
                                        Main.getDBDriver().getEventCollection()
                                                .updateOne(eq("_id", document.get("_id")), set("hasStarted", true));
                                        eventsToBeWritten.remove(document.getInteger("_id"));
                                    }
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        }
                    });

            // process entries with reminders
            query = and(eq("hasStarted",false), lte("reminders", new Date()));
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
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
                                    eventsToBeWritten.put(document.getInteger("_id"), new Object());
                                    Main.getDBDriver().getEventCollection()
                                            .updateOne(
                                                    eq("_id", document.get("_id")),
                                                    set("reminders", reminders));
                                    eventsToBeWritten.remove(document.getInteger("_id"));

                                    (new ScheduleEntry(document)).reloadDisplay();
                                }
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        }
                    });
        }
        else if( level == 1 )   // few minute check
        {
            Logging.info(this.getClass(), "Processing entries at level 1. . .");
            // adjust timers for entries starting/ending within the next hour
            Bson query = or(
                            and(
                                    eq("hasStarted",false),
                                    lte("start", Date.from(ZonedDateTime.now().plusHours(1).toInstant()))
                            ),
                            and(
                                    eq("hasStarted", true),
                                    lte("end", Date.from(ZonedDateTime.now().plusHours(1).toInstant())))
                            );
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        secondaryExecutor.execute(() -> {
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
        }
        else if( level == 2 )   // hourly check
        {
            Bson query = or(and(
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
            Logging.info(this.getClass(), "Processing entries at level 2. . .");
            // adjust timers for entries starting/ending within the next Day
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
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

            /// remove expiring events
            // delete message objects
            query = lte("expire", Date.from(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()));
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document->
                    {
                        ScheduleEntry se = new ScheduleEntry(document);
                        MessageUtilities.deleteMsg(se.getMessageObject(), null);
                    });

            // bulk delete entries from the database
            query = lte("expire", Date.from(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()));
            Main.getDBDriver().getEventCollection().deleteMany(query);
        }
        else if( level == 3 )   // daily check
        {
            Logging.info(this.getClass(), "Processing entries at level 3. . .");
            // adjust timers for entries that aren't starting/ending within the next day
            Bson query = or(
                            and(
                                    eq("hasStarted", false),
                                    gte("start", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))),
                            and(
                                    eq("hasStarted", true),
                                    gte("end", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))));
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
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
        }
    }
}
