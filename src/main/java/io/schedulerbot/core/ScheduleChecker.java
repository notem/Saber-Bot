package io.schedulerbot.core;

import io.schedulerbot.Main;
import io.schedulerbot.utils.__out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

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

    public ScheduleChecker(Collection<ScheduleEntry> entries, int level)
    {
        this.entries = entries;
        this.level = level;
    }

    public void run()
    {
        try
        {
            synchronized( Main.getScheduleLock() )
            {
                __out.printOut( this.getClass(), "Checking schedule at level " + level + ". . .");
                if( level == 0 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> fineCheck(entry, removeQueue));
                    Main.getFineTimerBuff().removeAll( removeQueue );
                }
                else if( level == 1 )
                {
                    ArrayList<ScheduleEntry> removeQueue = new ArrayList<>();
                    this.entries.forEach((entry) -> coarseCheck(entry, removeQueue));
                    Main.getCoarseTimerBuff().removeAll( removeQueue );
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
            __out.printOut( this.getClass(), "ERROR: " + Arrays.toString(e.getSuppressed()));
        }
    }

    private void fineCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        LocalTime moment = LocalTime.now();
        if (!entry.startFlag)
        {
            if ( moment.compareTo(entry.eStart) >= 0 )
            {
                Main.scheduleExec.submit( () -> {
                    // start event
                    entry.start();
                    entry.adjustTimer();
                });
            }
            else
            {
                // adjust the 'time until' displayed timer
                Main.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if (entry.eStart.isBefore(entry.eEnd)?
                    entry.eEnd.toSecondOfDay()-moment.toSecondOfDay()<=0:
                    (entry.eEnd.toSecondOfDay()-moment.toSecondOfDay()<=0 && entry.eStart.isBefore(moment)) )
            {
                Main.scheduleExec.submit( () -> {
                    synchronized( Main.getScheduleLock() )
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
                Main.scheduleExec.submit(entry::adjustTimer);
            }
        }
    }

    private void veryCoarseCheck(ScheduleEntry entry)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        LocalTime moment = LocalTime.now();
        LocalDate now = LocalDate.now();
        if (!entry.startFlag)
        {
            if( now.compareTo( entry.eDate ) > 0 )
            {
                Main.scheduleExec.submit( () -> {
                    synchronized( Main.getScheduleLock() )
                    {
                        // a rogue entry, destroy it
                        entry.destroy();
                    }
                });
            }
            else if( entry.eDate.isEqual(now) && entry.eStart.toSecondOfDay() - moment.toSecondOfDay() < 60*60 )
            {
                if( !Main.getFineTimerBuff().contains( entry ) )
                {
                    // if entry begins within an hour, add to the fineTimerBuff if not already there
                    Main.getFineTimerBuff().add(entry);
                }
            }
            else if( entry.eDate.isEqual(now) || entry.eDate.isEqual(now.plusDays(1)) )
            {
                if( !Main.getCoarseTimerBuff().contains( entry ) )
                {
                    // if entry begins within today or tomorrow, add to the coarseTimerBuff if not already there
                    Main.getCoarseTimerBuff().add(entry);
                }
            }
            else
            {
                // adjust the 'time until' displayed timer
                Main.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            if( !Main.getFineTimerBuff().contains( entry ) )
            {
                // if the entry already started, add to fineTimerBuff if not already there
                Main.getFineTimerBuff().add( entry );
            }
        }
    }

    private void coarseCheck(ScheduleEntry entry, Collection<ScheduleEntry> removeQueue)
    {
        __out.printOut( this.getClass(), "Processing " + Integer.toHexString(entry.eID) + "." );
        Integer timeTil = entry.eStart.toSecondOfDay() - LocalTime.now().toSecondOfDay();
        if (!entry.startFlag)
        {
            if( ((LocalDate.now().compareTo(entry.eDate)==0)?timeTil:timeTil+60*60*24) < 60*60 )
            {
                if( !Main.getFineTimerBuff().contains( entry ) )
                {
                    // if entry begins within an hour, add to the fineTimerBuff if not already there
                    Main.getFineTimerBuff().add(entry);
                    removeQueue.add( entry );       // queue it for removal from buffer
                }
            }
            else
            {
                // adjust the 'time until' displayed timer
                Main.scheduleExec.submit(entry::adjustTimer);
            }
        }
        else
        {
            Main.scheduleExec.submit(entry::adjustTimer);
        }
    }
}
