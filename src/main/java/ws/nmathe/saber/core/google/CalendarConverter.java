package ws.nmathe.saber.core.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.Logging;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.nin;

/**
 * Reads the next 7 days of events on a google calendar and converts
 * the events into a saber schedule entry. Events with recurrence are
 * condensed into one saber schedule entry.  Only daily and weekly by day
 * recurrence is supported.
 */
public class CalendarConverter
{
    /** Calendar service instance */
    private com.google.api.services.calendar.Calendar service;

    private static DateTimeFormatter rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void init()
    {
        // Build a new authorized API client service.
        // Note: Do not confuse this class with the
        //   com.google.api.services.calendar.model.Calendar class.
        try
        {
            service = GoogleAuth.getCalendarService();
            Main.getCommandHandler().putSync(); // enable the sync command
            Main.getScheduleManager().initScheduleSync(); // start the schedule sync timer
        }
        catch( IOException e )
        {
            Logging.warn(this.getClass(), e.getMessage());
        }
    }

    /**
     * verifies that an address url is a valid Calendar address Saber can
     * sync with
     * @param address (String) google calendar address
     * @return (boolean) true if valid
     */
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
            return false;
        }

    }

    /**
     * Purges a schedule from entries and adds events (after conversion)
     * from the next 7 day span of a Google Calendar
     * @param address (String) valid address of calendar
     * @param channel (MessageChannel) channel to sync with
     */
    public void syncCalendar(String address, TextChannel channel)
    {
        if(!Main.getScheduleManager().isASchedule(channel.getId()))
        {   // safety check to insure syncCalendar is being applied to a valid channel
            return;
        }

        Events events;
        String calLink;

        try
        {
            ZonedDateTime min = ZonedDateTime.now();
            ZonedDateTime max = min.plusDays(Main.getScheduleManager().getSyncLength(channel.getId()));

            events = service.events().list(address)
                    .setTimeMin(new DateTime(min.format(rfc3339Formatter)))
                    .setTimeMax(new DateTime(max.format(rfc3339Formatter)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(Main.getBotSettingsManager().getMaxEntries())
                    .execute();

            calLink = "https://calendar.google.com/calendar/embed?src=" + address;
        }
        catch( Exception e )
        {
            Logging.exception(this.getClass(), e);
            return;
        }

        // lock the schedule for syncing
        Main.getScheduleManager().lock(channel.getId());

        try
        {
            channel.sendTyping();   // send 'is typing' while the sync is in progress

            // change the zone to match the calendar
            ZoneId zone = ZoneId.of( events.getTimeZone() );
            Boolean syncZone = Main.getDBDriver().getScheduleCollection().find(eq("_id", channel.getId()))
                    .first().getBoolean("timezone_sync", false);
            if(syncZone)
            {
                Main.getScheduleManager().setTimeZone( channel.getId(), zone );
            }

            HashSet<String> uniqueEvents = new HashSet<>(); // a set of all unique (not child of a recurring event) events

            // convert every entry and add it to the scheduleManager
            for(Event event : events.getItems())
            {
                channel.sendTyping();   // continue to send 'is typing'

                ZonedDateTime start;
                ZonedDateTime end;
                String title;
                ArrayList<String> comments = new ArrayList<>();
                int repeat = 0;
                ZonedDateTime expire = null;

                if(event.getStart().getDateTime() == null)
                { // parse start and end dates for strange events
                    start = ZonedDateTime.of(
                            LocalDate.parse(event.getStart().getDate().toStringRfc3339()),
                            LocalTime.MIN,
                            zone);
                    end = ZonedDateTime.of(
                            LocalDate.parse(event.getEnd().getDate().toStringRfc3339()),
                            LocalTime.MIN,
                            zone);
                }
                else
                { // parse start and end times for normal events
                    start = ZonedDateTime.parse(event.getStart().getDateTime().toStringRfc3339(), rfc3339Formatter)
                            .withZoneSameInstant(zone);
                    end = ZonedDateTime.parse(event.getEnd().getDateTime().toStringRfc3339(), rfc3339Formatter)
                            .withZoneSameInstant(zone);
                }

                // get event title
                if( event.getSummary() == null )
                    title = "(No title)";
                else
                    title = event.getSummary();

                // process event description into event comments
                if( event.getDescription() != null )
                {
                    for( String comment : event.getDescription().split("\n") )
                    {
                        if( !comment.trim().isEmpty() )
                            comments.add( comment );
                    }
                }

                // handle event repeat/recurrence
                List<String> recurrence = event.getRecurrence();
                String recurrenceId = event.getRecurringEventId();
                if( recurrenceId != null )
                {
                    try
                    {
                        recurrence = service.events().get(address, recurrenceId).execute().getRecurrence();
                    }
                    catch( IOException e )
                    {
                        continue; // skip this event (I don't know what happened!)
                    }
                }
                if( recurrence != null )
                {
                    for( String rule : recurrence )
                    {
                        if( rule.startsWith("RRULE") && rule.contains("FREQ" ) )
                        {
                            // parse out the frequency of recurrence
                            String tmp = rule.split("FREQ=")[1].split(";")[0];
                            if( tmp.equals("DAILY" ) )
                            {
                                if(rule.contains("INTERVAL"))
                                {
                                    int interval = Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                                    repeat = (0b10000000 | interval);
                                }
                                else
                                {
                                    repeat = 0b1111111;
                                }
                            }
                            else if( tmp.equals("WEEKLY") && rule.contains("BYDAY") )
                            {
                                tmp = rule.split("BYDAY=")[1].split(";")[0];
                                repeat = ParsingUtilities.parseWeeklyRepeat(tmp);
                            }

                            // parse out the end date of recurrence
                            if( rule.contains("UNTIL=") )
                            {
                                tmp = rule.split("UNTIL=")[1].split(";")[0];
                                int year = Integer.parseInt(tmp.substring(0, 4));
                                int month = Integer.parseInt(tmp.substring(4,6));
                                int day = Integer.parseInt(tmp.substring(6, 8));

                                expire = ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.MIN, zone);
                            }
                        }
                    }
                }

                // add new event entry if the event has not already been added (ie, a repeating event)
                String googleId = recurrenceId==null ? event.getId() : recurrenceId;
                if(!uniqueEvents.contains(googleId))
                {
                    // if the google event already exists as a saber event on the schedule, update it
                    // otherwise add as a new saber event
                    Document doc = Main.getDBDriver().getEventCollection()
                            .find(and(
                                    eq("channelId", channel.getId()),
                                    eq("googleId", googleId))).first();
                    if(doc != null)
                    {
                        ScheduleEntry se = Main.getEntryManager().getEntry((Integer) doc.get("_id"));
                        Main.getEntryManager().updateEntry(se.getId(), title, start, end, comments, repeat,
                                event.getHtmlLink(), se.hasStarted(), se.getMessageObject(), googleId,
                                se.getRsvpYes(), se.getRsvpNo(), se.getRsvpUndecided(), se.isQuietStart(),
                                se.isQuietEnd(), se.isQuietRemind(), se.getRsvpMax(), se.getExpire());
                    }
                    else
                    {
                        boolean hasStarted = !start.isAfter(ZonedDateTime.now());
                        Main.getEntryManager().newEntry(title, start, end, comments, repeat,
                                event.getHtmlLink(), channel, googleId, hasStarted, expire);
                    }

                    uniqueEvents.add(recurrenceId==null ? event.getId() : recurrenceId);
                }
            }

            // purge channel of all entries on schedule that aren't in uniqueEvents
            Main.getDBDriver().getEventCollection()
                    .find(and(
                            eq("channelId", channel.getId()),
                            nin("googleId", uniqueEvents)))
                    .forEach((Consumer<? super Document>) document ->
                    {
                        ScheduleEntry entry = Main.getEntryManager().getEntry((Integer) document.get("_id"));
                        Message msg = entry.getMessageObject();
                        if( msg==null )
                            return;

                        Main.getEntryManager().removeEntry((Integer) document.get("_id"));
                        MessageUtilities.deleteMsg(msg, null);
                    });

            // set channel topic
            boolean hasPerms = channel.getGuild().getMember(Main.getBotJda().getSelfUser())
                    .hasPermission(channel, Permission.MANAGE_CHANNEL);
            if(hasPerms)
            {
                try
                {
                    channel.getManagerUpdatable().getTopicField().setValue(calLink).update().queue();
                }
                catch(PermissionException ignored)
                {}
                catch(Exception e)
                {
                    Logging.exception(this.getClass(), e);
                }
            }

        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
        finally
        {
            Main.getScheduleManager().unlock(channel.getId()); // syncing done, unlock the channel
        }

        // auto-sort
        int sortType = Main.getScheduleManager().getAutoSort(channel.getId());
        if(!(sortType == 0))
        {
            try // sleep for 2s before auto-sorting
            { Thread.sleep(2000); }
            catch (InterruptedException e)
            { Logging.warn(this.getClass(), e.getMessage()); }

            if(sortType == 1)
                Main.getScheduleManager().sortSchedule(channel.getId(), false);
            if(sortType == 2)
                Main.getScheduleManager().sortSchedule(channel.getId(), true);
        }
    }
}
