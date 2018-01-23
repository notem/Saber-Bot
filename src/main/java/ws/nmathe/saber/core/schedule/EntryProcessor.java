package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;

/**
 * Used by the Main scheduler timer, a new thread is executed every minute/5minutes/1hour/1day.
 * processes all entries referenced in the collection passed in, checking the start/end time and updating the
 * "time until" display timers.
 * a thread is spawned for each event operation to avoid one problematic event hanging-up the class
 */
class EntryProcessor implements Runnable
{
    // thread pool used to reload displays of events
    private static ExecutorService timerExecutor = Executors.newCachedThreadPool();

    // thread pool used to process events when emptying the queues
    private static ExecutorService queueExecutor = Executors.newFixedThreadPool(10);

    private enum queue { END_QUEUE, START_QUEUE, REMIND_QUEUE, ANNOUNCEMENT_QUEUE }
    private EntryManager.type type;
    private static Set<Integer> endQueue          = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Set<Integer> startQueue        = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Set<Integer> remindQueue       = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Set<Integer> announcementQueue = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** construct the entry processor with type */
    EntryProcessor(EntryManager.type type)
    {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public void run()
    {
        try
        {
            if(type == EntryManager.type.FILL)    // fill the queues
            {
                Logging.info(this.getClass(), "Processing entries: Filling queues. . .");

                // process entries which are ending
                Bson query = and(eq("hasStarted",true), lte("end", new Date()));
                processAndQueueEvents(queue.END_QUEUE, query);

                // process entries which are starting
                query = and(eq("hasStarted",false), lte("start", new Date()));
                processAndQueueEvents(queue.START_QUEUE, query);

                // process entries with reminders
                query = and(
                            and(eq("hasStarted",false), lte("reminders", new Date())),
                            gte("start", new Date()));
                processAndQueueEvents(queue.REMIND_QUEUE, query);

                // process entries with end reminders
                query = and(
                            and(eq("hasStarted",true), lte("end_reminders", new Date())),
                            gte("end", new Date()));
                processAndQueueEvents(queue.REMIND_QUEUE, query);

                // process entries with announcement overrides
                query = lte("announcements", new Date());
                processAndQueueEvents(queue.ANNOUNCEMENT_QUEUE, query);

                Logging.info(this.getClass(), "Finished filling queues.");
            }
            else if(type == EntryManager.type.EMPTY) // process and empty the queues
            {
                Logging.info(this.getClass(), "Processing entries: Emptying queues. . .");
                endQueue.forEach(entryId -> {
                    queueExecutor.submit(() -> {
                        Main.getEntryManager().getEntry(entryId).end();
                        endQueue.remove(entryId);
                    });
                });
                startQueue.forEach(entryId -> {
                    queueExecutor.submit(() -> {
                        Main.getEntryManager().getEntry(entryId).start();
                        startQueue.remove(entryId);
                    });
                });
                remindQueue.forEach(entryId -> {
                    queueExecutor.submit(() -> {
                        Main.getEntryManager().getEntry(entryId).remind();
                        remindQueue.remove(entryId);
                    });
                });
                announcementQueue.forEach(entryId -> {
                    queueExecutor.submit(() -> {
                        Main.getEntryManager().getEntry(entryId).announce();
                        announcementQueue.remove(entryId);
                    });
                });
                //Logging.info(this.getClass(), "Finished emptying queues.");
            }
            else
            {
                Bson query = new Document(); // should an invalid level ever be passed in, all entries will be reloaded!

                Logging.info(this.getClass(), "Processing entries: updating timers. . .");
                if(type == EntryManager.type.UPDATE1)
                {
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
                if(type == EntryManager.type.UPDATE2)
                {
                    // purge expiring events
                    query = lte("expire", Date.from(ZonedDateTime.now().plusDays(1).toInstant()));

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
                if(type == EntryManager.type.UPDATE3)
                {
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

                            timerExecutor.execute(() ->
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

    /**
     * fills a queue given a proper query, helper function to run()
     * @param queueIdentifier which queue to queue the event for
     * @param query the database query to use
     */
    private void processAndQueueEvents(queue queueIdentifier, Bson query)
    {
        Main.getDBDriver().getEventCollection().find(query)
                .forEach((Consumer<? super Document>) document ->
                {
                    // identify which shard is responsible for the schedule
                    String guildId = document.getString("guildId");
                    JDA jda = Main.getShardManager().getJDA(guildId);

                    // if the shard is not connected, do process the event
                    if(jda == null) return;
                    if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                    try
                    {
                        ScheduleEntry se = (new ScheduleEntry(document));
                        switch(queueIdentifier)
                        {
                            case END_QUEUE:
                                if(!endQueue.contains(se.getId()))
                                {
                                    endQueue.add(se.getId());
                                    //Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    // "\" ["+se.getId()+"] to the end queue");
                                }
                                break;
                            case REMIND_QUEUE:
                                if(!remindQueue.contains(se.getId()))
                                {
                                    remindQueue.add(se.getId());
                                    //Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    // "\" ["+se.getId()+"] to the remind queue");
                                }
                                break;
                            case START_QUEUE:
                                if(!startQueue.contains(se.getId()))
                                {
                                    startQueue.add(se.getId());
                                    //Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    // "\" ["+se.getId()+"] to the start queue");
                                }
                                break;
                            case ANNOUNCEMENT_QUEUE:
                                if(!announcementQueue.contains(se.getId()))
                                {
                                    announcementQueue.add(se.getId());
                                    //Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    // "\" ["+se.getId()+"] to the announce queue");
                                }
                                break;
                        }
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                });
    }
}
