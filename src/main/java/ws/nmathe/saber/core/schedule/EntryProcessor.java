package ws.nmathe.saber.core.schedule;

import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import java.time.ZonedDateTime;
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
    private static ExecutorService executor = Executors.newCachedThreadPool();

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
            Main.getDBDriver().getEventCollection().
                    find(and(
                            eq("hasStarted",true),
                            lte("end", new Date())))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
                            executor.submit(() -> {
                                // convert to scheduleEntry object and start
                                (new ScheduleEntry(document)).end();
                            });
                        }
                    });

            // process entries which are starting
            Main.getDBDriver().getEventCollection().
                    find(and(
                            eq("hasStarted",false),
                            lte("start", new Date())))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
                            executor.submit(() -> {
                                // convert to a POJO and start
                                (new ScheduleEntry(document)).start();

                                // if the entry isn't the special exception, update the db entry as started
                                if(!document.get("start").equals(document.get("end")))
                                {
                                    eventsToBeWritten.put(document.getInteger("_id"), new Object());
                                    Main.getDBDriver().getEventCollection()
                                            .updateOne(eq("_id", document.get("_id")), set("hasStarted", true));
                                    eventsToBeWritten.remove(document.getInteger("_id"));
                                }
                            });
                        }
                    });

            // process entries with reminders
            Main.getDBDriver().getEventCollection().
                    find(and(
                            eq("hasStarted",false),
                            lte("reminders", new Date())))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        if(!eventsToBeWritten.contains(document.getInteger("_id")))
                        {
                            executor.submit(() -> {
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
                            });
                        }
                    });
        }
        else if( level == 1 )   // few minute check
        {
            Logging.info(this.getClass(), "Processing entries at level 1. . .");
            // adjust timers for entries starting/ending within the next hour
            Main.getDBDriver().getEventCollection().
                    find(or(
                            and(
                                    eq("hasStarted",false),
                                    lte("start", Date.from(ZonedDateTime.now().plusHours(1).toInstant()))),
                            and(
                                    eq("hasStarted", true),
                                    lte("end", Date.from(ZonedDateTime.now().plusHours(1).toInstant())))))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        // convert to scheduleEntry object and start
                        (new ScheduleEntry(document)).reloadDisplay();
                    });
        }
        else if( level == 2 )   // hourly check
        {
            Logging.info(this.getClass(), "Processing entries at level 2. . .");
            // adjust timers for entries starting/ending within the next Day
            Main.getDBDriver().getEventCollection().
                    find(or(
                            and(
                                    eq("hasStarted",false),
                                    lte("start", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))),
                            and(
                                    eq("hasStarted", true),
                                    lte("end", Date.from(ZonedDateTime.now().plusDays(1).toInstant())))))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        // convert to scheduleEntry object and start
                        (new ScheduleEntry(document)).reloadDisplay();
                    });
        }
        else if( level == 3 )   // daily check
        {
            Logging.info(this.getClass(), "Processing entries at level 3. . .");
            // adjust timers for entries that aren't starting/ending within the next day
            Main.getDBDriver().getEventCollection().
                    find(or(
                            and(
                                    eq("hasStarted", false),
                                    gte("start", Date.from(ZonedDateTime.now().plusDays(1).toInstant()))),
                            and(
                                    eq("hasStarted", true),
                                    gte("end", Date.from(ZonedDateTime.now().plusDays(1).toInstant())))))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        // convert to scheduleEntry object and start
                        (new ScheduleEntry(document)).reloadDisplay();
                    });
        }
    }
}
