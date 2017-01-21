package ws.nmathe.saber.core.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.schedule.ScheduleEntryParser;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.__out;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 */
public class CalendarConverter
{
    /** Calendar service instance */
    private com.google.api.services.calendar.Calendar service;

    private static DateTimeFormatter rfc3339Formatter = DateTimeFormatter.ISO_DATE_TIME;

    public void init()
    {
        // Build a new authorized API client service.
        // Note: Do not confuse this class with the
        //   com.google.api.services.calendar.model.Calendar class.
        try
        {
            service = GoogleAuth.getCalendarService();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }

    public boolean checkValidAddress( String address )
    {
        try
        {
            Events events = service.events().list(address)
                    .setMaxResults(1)
                    .execute();
            return true;
        }
        catch( IOException e )
        {
            e.printStackTrace();
            return false;
        }

    }

    public void syncCalendar(String address, TextChannel channel) throws Exception
    {
        Events events;

        try
        {
            ZonedDateTime min = ZonedDateTime.now();
            ZonedDateTime max = min.plusDays(7);

            events = service.events().list(address)
                    .setTimeMin(new DateTime(min.toString()))
                    .setTimeMax(new DateTime(max.toString()))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
        }
        catch( IOException e )
        {
            e.printStackTrace();
            return;
        }

        // purge channel of all entries
        List<Integer> removeQueue = new ArrayList<>();
        for( Integer id : Main.getScheduleManager().getEntriesByChannel(channel.getId()) )
        {
            synchronized( Main.getScheduleManager().getScheduleLock() )
            {
                ScheduleEntry se = Main.getScheduleManager().getEntry( id );
                MessageUtilities.deleteMsg( se.getMessage(), null );

                removeQueue.add(id);
            }
        }
        synchronized( Main.getScheduleManager() )
        {
            for (Integer id : removeQueue)
            {
                Main.getScheduleManager().removeEntry(id);
            }
        }

        // change the zone to match the calendar
        ZoneId zone = ZoneId.of( events.getTimeZone() );
        Main.getChannelSettingsManager().setTimeZone( channel.getId(), zone );

        // convert every entry and add it to the scheduleManager
        for(Event event : events.getItems())
        {
            ZonedDateTime start;
            ZonedDateTime end;
            String title;
            ArrayList<String> comments = new ArrayList<>();
            int repeat = 0;

            __out.printOut(this.getClass(), event.getStart().getDateTime().toStringRfc3339());
            start = ZonedDateTime.parse(event.getStart().getDateTime().toStringRfc3339(), rfc3339Formatter);
            end = ZonedDateTime.parse(event.getEnd().getDateTime().toStringRfc3339(), rfc3339Formatter);

            if( event.getSummary() == null )
                title = "(No title)";
            else
                title = event.getSummary();

            if( event.getDescription() != null )
                Collections.addAll(comments, event.getDescription().split("\n"));

            List<String> recurrence = event.getRecurrence();
            String recurrenceId = event.getRecurringEventId();
            if( recurrenceId != null )
            {
                try
                {
                    recurrence = service.events().get(address, recurrenceId).execute()
                            .getRecurrence();
                }
                catch( IOException e )
                {
                    e.printStackTrace();
                }
            }
            if( recurrence != null )
            {
                // TODO parse recurrence rules into repeat bitset
                repeat = 0;
            }

            int id = Main.getScheduleManager().newId( null );
            String msg = ScheduleEntryParser.generate( title, start, end, comments, repeat, id, channel.getId());

            int finalRepeat = repeat;
            MessageUtilities.sendMsg( msg, channel, (message) ->
                    Main.getScheduleManager().addEntry( title, start, end, comments, id, message, finalRepeat));

            Thread.sleep(1000*4 );
        }
    }
}
