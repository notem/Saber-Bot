package ws.nmathe.saber.core.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 */
public class CalendarConverter
{
    /** Calendar service instance */
    private com.google.api.services.calendar.Calendar service;

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

    public List<ScheduleEntry> convertCalendar( String address )
    {
        Events events;
        try
        {
            events = service.events().list(address)
                    .setTimeMin(new DateTime(System.currentTimeMillis()))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
        }
        catch( IOException e )
        {
            e.printStackTrace();
            return new ArrayList<>();
        }
        List<ScheduleEntry> entryList = new ArrayList<>();

        // convert every entry and add it to the scheduleManager
        for(Event event : events.getItems())
        {
            // TODO
        }

        return entryList;
    }
}
