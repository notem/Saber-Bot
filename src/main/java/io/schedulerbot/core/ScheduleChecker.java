package io.schedulerbot.core;

import io.schedulerbot.Main;
import io.schedulerbot.utils.__out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Used by the Main scheduler timer, a new thread is executed every minute.
 * processes all entries in the entry maps, checking the start/end time and updating the
 * "time until" display timers.
 * additional threads are spawned to carry out tasks
 */
public class ScheduleChecker implements Runnable
{
    private Collection<ScheduleEntry> entries;

    public ScheduleChecker(HashMap<Integer, ScheduleEntry> entries)
    {
        this.entries = entries.values();
    }

    public void run()
    {
        try
        {
            synchronized( Main.scheduleLock )
            {
                this.entries.forEach(this::checkEntry);
            }
        }
        catch( Exception e )
        {
            __out.printOut( this.getClass(), "ERROR: " + Arrays.toString(e.getSuppressed()));
        }
    }

    private void checkEntry(ScheduleEntry entry)
    {
        LocalDate now = LocalDate.now();
        LocalTime moment = LocalTime.now();
        if (!entry.startFlag)
        {
            if (now.compareTo(entry.eDate) > 0)
            {
                Main.scheduleExec.submit( () -> {
                    synchronized( Main.scheduleLock )
                    {
                        // something odd happened, destroy entry
                        entry.destroy();
                    }
                });
            }
            else if (now.compareTo(entry.eDate) == 0 &&
                    (moment.compareTo(entry.eStart) >= 0))
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
            if (moment.compareTo(entry.eEnd) >= 0)
            {
                Main.scheduleExec.submit( () -> {
                    synchronized( Main.scheduleLock )
                    {
                        // end event
                        entry.end();
                    }
                });
            }
            else
            {
                // adjust the 'time until' displayed timer
                Main.scheduleExec.submit(entry::adjustTimer);
            }
        }
    }
}
