package io.schedulerbot.core;

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
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        ZonedDateTime now = ZonedDateTime.now();
        if (!entry.startFlag)
        {
            if ( now.compareTo(entry.eStart) >= 0 )
            {
                ScheduleManager.scheduleExec.submit( () -> {
                    // start event
                    entry.start();
                    entry.adjustTimer();
                });
            }
            else
            {
                // adjust the 'time until' displayed timer
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if (entry.eStart.isBefore(entry.eEnd)?
                    entry.eEnd.until(ZonedDateTime.now(), SECONDS)<=0:
                    (entry.eEnd.until(ZonedDateTime.now(), SECONDS)<=0 && entry.eStart.isBefore(now)) )
            {
                ScheduleManager.scheduleExec.submit( () -> {
                    synchronized( scheduleManager.getScheduleLock() )
                    {
                        // end event
                        entry.end();
                    }
                });
                removeQueue.add( entry );
            }
            else
            {
                // adjust the 'time until' displayed timer
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
            if( now.getDayOfYear() > entry.eStart.getDayOfYear() )
            {
                ScheduleManager.scheduleExec.submit( () -> {
                    synchronized( scheduleManager.getScheduleLock() )
                    {
                        // a rogue entry, destroy it
                        entry.destroy();
                    }
                });
            }
            else if( entry.eStart.isEqual(now) && entry.eStart.toEpochSecond() - now.toEpochSecond() < 60*60 )
            {
                if( !scheduleManager.getFineTimerBuff().contains( entry ) )
                {
                    // if entry begins within an hour, add to the fineTimerBuff if not already there
                    scheduleManager.getFineTimerBuff().add(entry);
                }
            }
            else if( entry.eStart.isEqual(now) || entry.eStart.isEqual(now.plusDays(1)) )
            {
                if( !scheduleManager.getCoarseTimerBuff().contains( entry ) )
                {
                    // if entry begins within today or tomorrow, add to the coarseTimerBuff if not already there
                    scheduleManager.getCoarseTimerBuff().add(entry);
                }
            }
            else
            {
                // adjust the 'time until' displayed timer
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if( !scheduleManager.getFineTimerBuff().contains( entry ) )
            {
                // if the entry already started, add to fineTimerBuff if not already there
                scheduleManager.getFineTimerBuff().add( entry );
            }
        }
    }

    private void coarseCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        long timeTil = entry.eStart.toEpochSecond() - ZonedDateTime.now().toEpochSecond();
        if (!entry.startFlag)
        {
            if( ((ZonedDateTime.now().compareTo(entry.eStart)==0)?timeTil:timeTil+60*60*24) < 60*60 )
            {
                if( !scheduleManager.getFineTimerBuff().contains( entry ) )
                {
                    // if entry begins within an hour, add to the fineTimerBuff if not already there
                    scheduleManager.getFineTimerBuff().add(entry);
                    removeQueue.add( entry );       // queue it for removal from buffer
                }
            }
            else
            {
                // adjust the 'time until' displayed timer
                ScheduleManager.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            ScheduleManager.scheduleExec.submit(entry::adjustTimer);
        }
    }
}
