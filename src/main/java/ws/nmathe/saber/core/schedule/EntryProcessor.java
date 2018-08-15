package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.exceptions.PermissionException;
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

    // thread pool used to process events when emptying the announcement sets
    private static ExecutorService setExecutor = Executors.newCachedThreadPool();

    private enum ActionType {END, START, REMIND, SPECIAL}
    private EntryManager.type type;

    // simple mechanism to avoid conflicting database updates
    // (ie. simultaneous remind() and announce() update)
    private static Set<Integer> processing = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            /*
             * Process events
             */
            if(type == EntryManager.type.PROCESS)
            {
                Logging.info(this.getClass(), "Processing entries. . .");
                Bson query;

                /*
                 * process any event-specific announcements
                 */
                query = lte("announcements", new Date());
                processEvents(ActionType.SPECIAL, query);

                /*
                 * process events to end
                 */
                query = and(eq("hasStarted",true), lte("end", new Date()));
                processEvents(ActionType.END, query);

                /*
                 * process events to start
                 */
                query = and(eq("hasStarted",false), lte("start", new Date()));
                processEvents(ActionType.START, query);

                /*
                 * process events with reminders
                 */
                query = and(
                            and(eq("hasStarted",false), lte("reminders", new Date())),
                            gte("start", new Date()));
                query = or(query,
                            and(
                                and(eq("hasStarted",true), lte("end_reminders", new Date())),
                                gte("end", new Date())));
                processEvents(ActionType.REMIND, query);

                Logging.info(this.getClass(), "Currently processing "+processing.size()+" events.");
            }

            /*
             * Updates the 'starts in x minutes' timer on events
             */
            else
            {
                // dummy document query will filter all events
                // should an invalid level ever be passed in, all entries will be reloaded!
                Bson query = new Document();

                Logging.info(this.getClass(), "Processing entries: updating timers. . .");
                if(type == EntryManager.type.UPDATE1)
                {   // adjust timers for entries starting/ending within the next hour
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
                {   // purge expiring events
                    query = lte("expire", Date.from(ZonedDateTime.now().plusDays(1).toInstant()));

                    //delete message objects
                    Main.getDBDriver().getEventCollection().find(query)
                            .forEach((Consumer<? super Document>) document ->
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
                {   // adjust timers for entries that aren't starting/ending within the next day
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
                                {   // convert to scheduleEntry object and update display
                                    (new ScheduleEntry(document)).reloadDisplay();
                                }
                                catch (PermissionException ignored)
                                {/* dont care */}
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });

                Logging.info(this.getClass(), "Finished updating timers. . .");
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }

    /**
     * fills a ActionType given a proper query, helper function to run()
     * @param action which ActionType to ActionType the event for
     * @param query the database query to use
     */
    private void processEvents(ActionType action, Bson query)
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

                    ScheduleEntry se = (new ScheduleEntry(document));
                    Integer id = se.getId();
                    if (processing.add(id))
                    {
                        setExecutor.submit(() ->
                        {
                            try
                            {
                                switch(action)
                                {
                                    case END:
                                        se.end();
                                        break;
                                    case START:
                                        se.start();
                                        break;
                                    case REMIND:
                                        se.remind();
                                        break;
                                    case SPECIAL:
                                        se.announce();
                                        break;
                                }
                            }
                            catch (PermissionException e)
                            {
                                Logging.warn(this.getClass(),
                                        "Permission error on '"+se.getTitle()+"' ["+se.getId()+"]: "+e.getMessage());
                            }
                            catch (Exception e)
                            {
                                Logging.exception(this.getClass(), e);
                            }
                            finally
                            {
                                processing.remove(id);
                            }
                        });
                    }
                });
    }
}
