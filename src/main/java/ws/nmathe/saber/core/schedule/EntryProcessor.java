package ws.nmathe.saber.core.schedule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
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
    // thread pool used to process events
    private static ExecutorService eventsExecutor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("EntryProcessor-%d").build());

    private static ExecutorService timerExecutor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("EntryTimer-%d").build());

    private enum SetType {END_SET, START_SET, REMIND_SET, SPECIAL_SET}
    private EntryManager.type type;
    private static Set<Integer> endSet      = Collections.newSetFromMap(new ConcurrentHashMap<>()); // event-end announcements
    private static Set<Integer> startSet    = Collections.newSetFromMap(new ConcurrentHashMap<>()); // event-start announcements
    private static Set<Integer> remindSet   = Collections.newSetFromMap(new ConcurrentHashMap<>()); // reminders
    private static Set<Integer> specialSet  = Collections.newSetFromMap(new ConcurrentHashMap<>()); // event-specific announcements

    // this set is used to track which events are currently being processed and should be ignored
    // if they appear in later database queries
    private static Set<Integer> processing  = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            Mode 1
            Fills the sets which events which have announcements that should be processed
            */
            if(type == EntryManager.type.FILL)
            {
                Logging.info(this.getClass(), "Processing entries: Filling queues. . .");

                fillSet(SetType.END_SET);
                fillSet(SetType.START_SET);
                fillSet(SetType.REMIND_SET);
                fillSet(SetType.SPECIAL_SET);

                Logging.info(this.getClass(), "Finished filling queues.");
            }

            /*
            Mode 2
            Processes the events in each set
            */
            else if (type == EntryManager.type.EMPTY)
            {
                Logging.info(this.getClass(), "Processing entries: Emptying queues...");

                emptySet(SetType.END_SET);
                emptySet(SetType.START_SET);
                emptySet(SetType.REMIND_SET);
                emptySet(SetType.SPECIAL_SET);

                Logging.info(this.getClass(), "There are "+processing.size()+" events currently processing.");
            }

            /*
            Mode 3
            Updates the 'starts in x minutes' timer on events and deletes expired events
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
                        (new ScheduleEntry(document)).getMessageObject((message) ->
                        {
                            MessageUtilities.deleteMsg(message, null);
                        });
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
                                catch(Exception e)
                                {
                                    Logging.exception(this.getClass(), e);
                                }
                            });
                        });
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }

    /**
     *
     * @param setIdentifier
     */
    private void emptySet(SetType setIdentifier)
    {
        Set<Integer> set = new HashSet<>();
        switch (setIdentifier)
        {
            case END_SET:
                set = new HashSet<>(endSet);
                break;
            case START_SET:
                set = new HashSet<>(startSet);
                break;
            case REMIND_SET:
                set = new HashSet<>(remindSet);
                break;
            case SPECIAL_SET:
                set = new HashSet<>(specialSet);
                break;
        }
        set.forEach(entryId ->
        {
            if (processing.add(entryId))
            {
                // submit the event's task to the executor
                eventsExecutor.submit(() ->
                {
                    try
                    {
                        ScheduleEntry entry = Main.getEntryManager().getEntry(entryId);
                        if (entry != null)
                        {
                            switch (setIdentifier)
                            {
                                case END_SET:
                                    entry.end();
                                    endSet.remove(entryId);
                                    break;
                                case START_SET:
                                    entry.start();
                                    startSet.remove(entryId);
                                    break;
                                case REMIND_SET:
                                    entry.remind();
                                    remindSet.remove(entryId);
                                    break;
                                case SPECIAL_SET:
                                    entry.announce();
                                    specialSet.remove(entryId);
                                    break;
                            }
                        }
                    }
                    catch(PermissionException e)
                    {
                        Logging.warn(this.getClass(), "Lacking permission: "+e.getPermission());
                    }
                    catch (Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                    finally
                    {
                        processing.remove(entryId);
                    }
                });
            }
        });
    }

    /**
     * fills a SetType given a proper query, helper function to run()
     * @param setIdentifier which SetType to SetType the event for
     */
    private void fillSet(SetType setIdentifier)
    {
        Bson query = new BsonDocument();
        switch(setIdentifier)
        {
            case END_SET:
                query = and(eq("hasStarted",true), lte("end", new Date()));
                break;
            case REMIND_SET:
                query = and(
                        and(eq("hasStarted",true), lte("end_reminders", new Date())),
                        gte("end", new Date()));
                query = or(query, and(
                        and(eq("hasStarted",false), lte("reminders", new Date())),
                        gte("start", new Date())));
                break;
            case START_SET:
                query = and(eq("hasStarted",false), lte("start", new Date()));
                break;
            case SPECIAL_SET:
                query = lte("announcements", new Date());
                break;
        }
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
                        switch(setIdentifier)
                        {
                            case END_SET:
                                if(!endSet.contains(se.getId()))
                                {
                                    endSet.add(se.getId());
                                    Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    "\" ["+se.getId()+"] to the end set");
                                }
                                break;
                            case REMIND_SET:
                                if(!remindSet.contains(se.getId()))
                                {
                                    remindSet.add(se.getId());
                                    Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    "\" ["+se.getId()+"] to the remind set");
                                }
                                break;
                            case START_SET:
                                if(!startSet.contains(se.getId()))
                                {
                                    startSet.add(se.getId());
                                    Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    "\" ["+se.getId()+"] to the start set");
                                }
                                break;
                            case SPECIAL_SET:
                                if(!specialSet.contains(se.getId()))
                                {
                                    specialSet.add(se.getId());
                                    Logging.info(this.getClass(), "Added \"" + se.getTitle() +
                                    "\" ["+se.getId()+"] to the announce set");
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
