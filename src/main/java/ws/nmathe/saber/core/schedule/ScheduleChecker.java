package ws.nmathe.saber.core.schedule;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.__out;

import java.time.ZonedDateTime;
import java.util.*;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Used by the Main scheduler timer, a new thread is executed every minute/15minutes/12hours.
 * processes all entries referenced in the collection passed in, checking the start/end time and updating the
 * "time until" display timers.
 * additional threads are spawned to carry out tasks
 */
class ScheduleChecker implements Runnable
{
    private Collection<ScheduleEntry> entries;
    private int level;

    private ScheduleManager schedManager = Main.getScheduleManager();

    ScheduleChecker(Collection<ScheduleEntry> entries, int level)
    {
        this.entries = entries;
        this.level = level;
    }

    public void run()
    {
        try
        {
            synchronized( schedManager.getScheduleLock() )
            {
                __out.printOut( this.getClass(), "Checking schedule at level " + level + ". . .");
                if( level == 0 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> fineCheck(entry, removeQueue));
                    schedManager.getFineTimerBuff().removeAll( removeQueue );
                }
                else if( level == 1 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> coarseCheck(entry, removeQueue));
                    schedManager.getCoarseTimerBuff().removeAll( removeQueue );
                }
                else if( level == 2 )
                {
                    this.entries.forEach(this::veryCoarseCheck);
                }
                __out.printOut( this.getClass(), "Finished checking schedule at level " + level + ".");
            }
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }

    /**
     * the checker function for the 1 minute timer.
     * @param entry entry to check
     * @param removeQueue a queue of entries to remove after all entries are checked
     */
    private void fineCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        ZonedDateTime now = ZonedDateTime.now();
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.getId()) + ". Starting in " +
                now.until(entry.getStart(), SECONDS) + ": ending in " + now.until(entry.getEnd(),SECONDS) );

        if (!entry.hasStarted())
        {
            if ( now.until(entry.getStart(), SECONDS) <= 0 )
            {
                schedManager.getExecutor().submit(entry::start);
            }
            else
            {
                schedManager.getExecutor().submit(entry::adjustTimer);
            }
        }
        else
        {
            if ( now.until(entry.getEnd(), SECONDS) <= 0 )
            {
                schedManager.getExecutor().submit(entry::end);
                removeQueue.add( entry );
            }
            else
            {
                schedManager.getExecutor().submit(entry::adjustTimer);
            }
        }
    }

    /**
     * the 30 minute checker, moves entries into the fine timer if they begin within the hour
     * @param entry entry to check
     * @param removeQueue a queue of entries to remove after all entries are checked
     */
    private void coarseCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.getId()) + "." );
        ZonedDateTime now = ZonedDateTime.now();
        if (!entry.hasStarted())
        {
            if( now.until(entry.getStart(), SECONDS) < 60*60 )
            {
                if( !schedManager.getFineTimerBuff().contains( entry ) )
                {
                    schedManager.getFineTimerBuff().add(entry);
                    removeQueue.add( entry );       // queue it for removal from buffer
                }
            }
            else
            {
                schedManager.getExecutor().submit(entry::adjustTimer);
            }
        }
        else
        {
            schedManager.getExecutor().submit(entry::adjustTimer);
        }
    }

    /**
     * the 12h timer checker. Examine's every active entry, moving entries to the fine
     * buffer or coarse buffer if appropriate.
     * @param entry ScheduleEntry to check
     */
    private void veryCoarseCheck(ScheduleEntry entry)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.getId()) + "." );
        ZonedDateTime now = ZonedDateTime.now();
        if (!entry.hasStarted())
        {
            if( now.until(entry.getStart(), SECONDS) < 60*60 )
            {
                if( !schedManager.getFineTimerBuff().contains( entry ) )
                {
                    schedManager.getFineTimerBuff().add(entry);
                }
            }
            else if( now.until(entry.getStart(), SECONDS) < 32*60*60 )
            {
                if( !schedManager.getCoarseTimerBuff().contains( entry ) )
                {
                    schedManager.getCoarseTimerBuff().add(entry);
                }
            }
            else
            {
                schedManager.getExecutor().submit(entry::adjustTimer);
            }
        }
        else
        {
            if( !schedManager.getFineTimerBuff().contains( entry ) )
            {
                schedManager.getFineTimerBuff().add( entry );
            }
        }
    }
}
