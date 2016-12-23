package io.schedulerbot.core.schedule;

import io.schedulerbot.Main;
import io.schedulerbot.utils.__out;

import java.time.ZonedDateTime;
import java.util.*;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Used by the Main scheduler timer, a new thread is executed every minute.
 * processes all entries referenced in the collection passed in, checking the start/end time and updating the
 * "time until" display timers.
 * additional threads are spawned to carry out tasks
 */
public class ScheduleChecker implements Runnable
{
    private Collection<ScheduleEntry> entries;
    private int level;
    private ScheduleManager scheduleManager = Main.scheduleManager;

    public ScheduleChecker(Collection<ScheduleEntry> entries, int level)
    {
        this.entries = entries;
        this.level = level;
    }

    public void run()
    {
        try
        {
            synchronized( scheduleManager.getScheduleLock() )
            {
                __out.printOut( this.getClass(), "Checking schedule at level " + level + ". . .");
                if( level == 0 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> fineCheck(entry, removeQueue));
                    scheduleManager.getFineTimerBuff().removeAll( removeQueue );
                }
                else if( level == 1 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> coarseCheck(entry, removeQueue));
                    scheduleManager.getCoarseTimerBuff().removeAll( removeQueue );
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

    private void fineCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        ZonedDateTime now = ZonedDateTime.now();
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + ". Starting in " + now.until(entry.eStart, SECONDS) + ": ending in " + now.until(entry.eEnd,SECONDS) );
        if (!entry.startFlag)
        {
            if ( now.until(entry.eStart, SECONDS) <= 0 )
            {
                ScheduleManager.scheduleExec.submit(entry::start);
            }
            else
            {
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if ( now.until(entry.eEnd, SECONDS) <= 0 )
            {
                ScheduleManager.scheduleExec.submit(entry::end);
                removeQueue.add( entry );
            }
            else
            {
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
    }

    private void veryCoarseCheck(ScheduleEntry entry)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        ZonedDateTime now = ZonedDateTime.now();
        if (!entry.startFlag)
        {
            if( now.until(entry.eStart, SECONDS) < 60*60 )
            {
                if( !scheduleManager.getFineTimerBuff().contains( entry ) )
                {
                    scheduleManager.getFineTimerBuff().add(entry);
                }
            }
            else if( now.until(entry.eStart, SECONDS) < 32*60*60 )
            {
                if( !scheduleManager.getCoarseTimerBuff().contains( entry ) )
                {
                    scheduleManager.getCoarseTimerBuff().add(entry);
                }
            }
            else
            {
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if( !scheduleManager.getFineTimerBuff().contains( entry ) )
            {
                scheduleManager.getFineTimerBuff().add( entry );
            }
        }
    }

    private void coarseCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        ZonedDateTime now = ZonedDateTime.now();
        if (!entry.startFlag)
        {
            if( now.until(entry.eStart, SECONDS) < 60*60 )
            {
                if( !scheduleManager.getFineTimerBuff().contains( entry ) )
                {
                    scheduleManager.getFineTimerBuff().add(entry);
                    removeQueue.add( entry );       // queue it for removal from buffer
                }
            }
            else
            {
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            ScheduleManager.scheduleExec.submit(entry::adjustTimer);
        }
    }
}
