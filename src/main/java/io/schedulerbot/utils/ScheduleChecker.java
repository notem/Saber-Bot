package io.schedulerbot.utils;

import io.schedulerbot.Main;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 */
public class ScheduleChecker implements Runnable
{
    private Collection<EventEntry> entries;

    public ScheduleChecker(HashMap<Integer, EventEntry> entries)
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
            __out.printOut( this.getClass(), "Dump: " + Arrays.toString(e.getSuppressed()));
        }
    }

    private void checkEntry(EventEntry entry)
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
