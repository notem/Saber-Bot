package ws.nmathe.saber.core.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.EntryManager;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.VerifyUtilities;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;

/**
 * Reads the next 7 days of events on a google calendar and converts
 * the events into a saber schedule entry. Events with recurrence are
 * condensed into one saber schedule entry.  Only daily and weekly by day
 * recurrence is supported.
 */
public class CalendarConverter
{
    /** DateTimeFormatter that is RFC3339 compliant  */
    private static DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void init()
    {
        try
        {
            // Build a new authorized API client service.
            // Note: Do not confuse this class with the
            //   com.google.api.services.calendar.model.Calendar class.
            /** if this fails, do not enable schedule synchronization **/
            Calendar service = GoogleAuth.getCalendarService(GoogleAuth.authorize());

            Main.getCommandHandler().putSync(); // enable the sync command
            Main.getScheduleManager().initScheduleSync(); // start the schedule sync timer
        }
        catch( IOException e )
        {
            Logging.warn(this.getClass(), e.getMessage());
        }
        catch( Exception e )
        {
            Logging.exception(this.getClass(), e);
        }
    }

    /**
     * verifies that an address url is a valid Calendar address Saber can
     * sync with
     * @param address (String) google calendar address
     * @return (boolean) true if valid
     */
    public boolean checkValidAddress(String address, Calendar service)
    {
        try
        {
            service.events().list(address)
                    .setTimeMin(new DateTime(ZonedDateTime.now().format(RFC3339_FORMATTER)))
                    .setTimeMax(new DateTime(ZonedDateTime.now().plusDays(7).format(RFC3339_FORMATTER)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(Main.getBotSettingsManager().getMaxEntries())
                    .execute();

            return true;
        }
        catch( Exception e )
        {
            return false;
        }

    }


    /**
     *
     * @param address
     * @param channel
     */
    public void exportCalendar(String address, TextChannel channel, Calendar service)
    {
        if(channel == null || address == null) return;
        if(!Main.getScheduleManager().isASchedule(channel.getId()))
        {   // safety check to insure importCalendar is being applied to a valid channel
            return;
        }

        Collection<ScheduleEntry> entries = Main.getEntryManager().getEntriesFromChannel(channel.getId());
        entries.stream().forEach(se->
        {
            // compose the event's description
            String description = String.join("\n", se.getComments());
            if(se.getImageUrl() != null) description += "\nimage: " + se.getImageUrl();
            if(se.getThumbnailUrl() != null) description += "\nthumbnail: " + se.getThumbnailUrl();
            if(se.getDeadline() != null) description += "\ndeadline: " + se.getDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE);
            for(String key : se.getRsvpLimits().keySet())
            {
                description += "\nlimit: " + key + " " + se.getRsvpLimit(key);
            }

            ZoneId zone = Main.getScheduleManager().getTimeZone(channel.getId());
            EventDateTime start = new EventDateTime()
                    .setDateTime(new DateTime(Date.from(se.getStart().toInstant())))
                    .setTimeZone(zone.getId());
            EventDateTime end = new EventDateTime()
                    .setDateTime(new DateTime(Date.from(se.getEnd().toInstant())))
                    .setTimeZone(zone.getId());

            Event event = new Event();
            event.setDescription(description)
                    .setSummary(se.getTitle())
                    .setRecurrence(toRFC5545(se.getRepeat(), se.getExpire(), zone.getRules().getOffset(Instant.now())))
                    .setStart(start)
                    .setEnd(end);

            try // interface with google calendar api
            {
                if(se.getGoogleId() != null)
                {
                    event.setICalUID(se.getGoogleId());
                    service.events().update(address, event.getICalUID(), event).execute();
                }
                else
                {
                    Event e = service.events().insert(address, event).execute();
                    Logging.info(this.getClass(), e.getId());
                    Main.getEntryManager().updateEntry(se, false);
                }
            }
            catch( Exception e )
            {
                Logging.exception(this.getClass(), e);
                return;
            }
        });
    }

    /**
     * Purges a schedule from entries and adds events (after conversion)
     * from the next 7 day span of a Google Calendar
     * @param address (String) valid address of calendar
     * @param channel (MessageChannel) channel to sync with
     */
    public void importCalendar(String address, TextChannel channel, Calendar service)
    {
        if(channel == null || address == null) return;
        if(!Main.getScheduleManager().isASchedule(channel.getId()))
        {   // safety check to insure importCalendar is being applied to a valid channel
            return;
        }

        JDA jda = Main.getShardManager().getJDA(channel.getGuild().getId());

        Events events;
        String calLink;

        try
        {
            ZonedDateTime min = ZonedDateTime.now();
            ZonedDateTime max = min.plusDays(Main.getScheduleManager().getSyncLength(channel.getId()));

            events = service.events().list(address)
                    .setTimeMin(new DateTime(min.format(RFC3339_FORMATTER)))
                    .setTimeMax(new DateTime(max.format(RFC3339_FORMATTER)))
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

        try
        {
            // lock the schedule for syncing
            Main.getScheduleManager().lock(channel.getId());
            channel.sendTyping().queue();   // send 'is typing' while the sync is in progress

            // change the zone to match the calendar
            // only if the zone has not been manually set for that schedule
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
                channel.sendTyping().queue();   // continue to send 'is typing'

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
                    start = ZonedDateTime.parse(event.getStart().getDateTime().toStringRfc3339(), RFC3339_FORMATTER)
                            .withZoneSameInstant(zone);
                    end = ZonedDateTime.parse(event.getEnd().getDateTime().toStringRfc3339(), RFC3339_FORMATTER)
                            .withZoneSameInstant(zone);
                }

                // get event title
                if(event.getSummary() == null)
                {
                    title = "(No title)";
                }
                else
                {
                    title = event.getSummary();
                }

                // process event description into event comments or other settings
                String imageUrl = null;
                String thumbnailUrl = null;
                ZonedDateTime rsvpDeadline = null;
                Map<String, Integer> rsvpLimits = new HashMap<>();
                if( event.getDescription() != null )
                {
                    for( String comment : event.getDescription().split("\n") )
                    {
                        // image
                        if(comment.trim().toLowerCase().startsWith("image:"))
                        {
                            String[] tmp = comment.trim().split(":",2); // split to limit:
                            if(tmp.length > 1)
                            {
                                imageUrl = tmp[1].trim();
                                if (!VerifyUtilities.verifyUrl(imageUrl)) imageUrl = null;
                            }
                        }
                        // thumbnail
                        else if(comment.trim().toLowerCase().startsWith("thumbnail:"))
                        {
                            String[] tmp = comment.trim().split(":",2); // split to limit:
                            if(tmp.length > 1)
                            {
                                thumbnailUrl = tmp[1].trim();
                                if(!VerifyUtilities.verifyUrl(thumbnailUrl)) thumbnailUrl = null;
                            }
                        }
                        // limit
                        else if(comment.trim().toLowerCase().startsWith("limit:"))
                        {
                            String[] tmp = comment.trim().split(":",2); // split to limit:
                            if(tmp.length > 1)
                            {
                                String[] str = tmp[1].trim().split("[^\\S\n\r]+"); // split into white space separate segments
                                if(str.length >= 2)
                                {
                                    // rebuild the rsvp group name
                                    String name = "";
                                    for(int i=0; i<str.length-1; i++)
                                    {
                                        name += str[i];
                                        if(i != str.length-2) name += " ";
                                    }

                                    // parse the limit
                                    Integer limit = -1;
                                    if(VerifyUtilities.verifyInteger(str[str.length-1]))
                                    {
                                        limit = Integer.parseInt(str[str.length-1]);
                                    }

                                    rsvpLimits.put(name, limit);
                                }
                            }

                        }
                        // deadline
                        else if(comment.trim().toLowerCase().startsWith("deadline:"))
                        {
                            String tmp = comment.trim().toLowerCase().replace("deadline:","").trim();
                            if(VerifyUtilities.verifyDate(tmp))
                            {
                                rsvpDeadline = ZonedDateTime.of(ParsingUtilities.parseDateStr(tmp), LocalTime.MAX, zone);
                            }
                        }
                        else if(!comment.trim().isEmpty())
                        {
                            comments.add( comment );
                        }
                    }
                }

                // handle event repeat/recurrence
                List<String> recurrence = event.getRecurrence();
                String recurrenceId = event.getRecurringEventId();
                if(recurrenceId != null)
                {
                    try
                    {
                        recurrence = service.events().get(address, recurrenceId).execute().getRecurrence();
                    }
                    catch(IOException e)
                    {
                        recurrence = null;
                    }
                }
                if(recurrence != null)
                {
                    for(String rule : recurrence)
                    {
                        if(rule.startsWith("RRULE") && rule.contains("FREQ" ))
                        {
                            // parse out the frequency of recurrence
                            String tmp = rule.split("FREQ=")[1].split(";")[0];
                            if(tmp.equals("DAILY" ))
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
                            else if(tmp.equals("WEEKLY") && rule.contains("BYDAY"))
                            {
                                tmp = rule.split("BYDAY=")[1].split(";")[0];
                                repeat = ParsingUtilities.parseWeeklyRepeat(tmp);
                            }

                            // parse out the end date of recurrence
                            if(rule.contains("UNTIL="))
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

                    // should the event be flagged as already started?
                    boolean hasStarted = start.isBefore(ZonedDateTime.now());

                    // update an existing event
                    if(doc != null && (new ScheduleEntry(doc)).getMessageObject() != null)
                    {
                        ScheduleEntry se = (new ScheduleEntry(doc))
                                .setTitle(title)
                                .setStart(start)
                                .setEnd(end)
                                .setRepeat(repeat)
                                .setTitleUrl(event.getHtmlLink())
                                .setGoogleId(googleId)
                                .setExpire(expire)
                                .setImageUrl(imageUrl)
                                .setStarted(hasStarted)
                                .setThumbnailUrl(thumbnailUrl)
                                .setRsvpLimits(rsvpLimits)
                                .setComments(comments)
                                .setRsvpDeadline(rsvpDeadline);

                        Main.getEntryManager().updateEntry(se, false);
                    }
                    else // create a new event
                    {
                        ScheduleEntry se = (new ScheduleEntry(channel, title, start, end))
                                .setRepeat(repeat)
                                .setTitleUrl(event.getHtmlLink())
                                .setGoogleId(googleId)
                                .setExpire(expire)
                                .setImageUrl(imageUrl)
                                .setThumbnailUrl(thumbnailUrl)
                                .setStarted(hasStarted)
                                .setRsvpLimits(rsvpLimits)
                                .setComments(comments)
                                .setRsvpDeadline(rsvpDeadline);

                        Main.getEntryManager().newEntry(se, false);
                    }

                    uniqueEvents.add(recurrenceId==null ? event.getId() : recurrenceId);
                }
            }

            // purge channel of all entries on schedule that aren't in uniqueEvents
            Bson query = and(
                            eq("channelId", channel.getId()),
                            nin("googleId", uniqueEvents));

            Main.getDBDriver().getEventCollection()
                    .find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        ScheduleEntry entry = Main.getEntryManager().getEntry((Integer) document.get("_id"));
                        Message msg = entry.getMessageObject();
                        if( msg==null ) return;

                        Main.getEntryManager().removeEntry((Integer) document.get("_id"));
                        MessageUtilities.deleteMsg(msg, null);
                    });

            // set channel topic
            boolean hasPerms = channel.getGuild().getMember(jda.getSelfUser()).hasPermission(channel, Permission.MANAGE_CHANNEL);
            if(hasPerms)
            {
                channel.getManagerUpdatable().getTopicField().setValue(calLink).update().queue();
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
        EntryManager.autoSort(true, channel.getId());
    }


    private static List<String> toRFC5545(Integer repeat, ZonedDateTime expire, ZoneOffset zone)
    {
        List<String> recurrence = new ArrayList<>();
        if(repeat == 0 || repeat > 0b11111111)
        {
            return recurrence;
        }

        String rule = "RRULE:";
        if((repeat&0b10000000) == 0b10000000) // interval
        {
            rule += "FREQ=DAILY;";
            int tmp = repeat & 0b01111111;    // take the first 7 bits
            rule += "INTERVAL=" + tmp + ";";
        }
        else // weekly
        {
            if(repeat == 0b1111111) // every day
            {
                rule += "FREQ=DAILY;";
            }
            else
            {
                rule += "FREQ=WEEKLY;BYDAY=";
                List<String> tmp = new ArrayList<>();
                if((repeat&0b0000001) == 0b0000001) tmp.add("SU");
                if((repeat&0b0000010) == 0b0000010) tmp.add("MO");
                if((repeat&0b0000100) == 0b0000100) tmp.add("TU");
                if((repeat&0b0001000) == 0b0001000) tmp.add("WE");
                if((repeat&0b0010000) == 0b0010000) tmp.add("TH");
                if((repeat&0b0100000) == 0b0100000) tmp.add("FR");
                if((repeat&0b1000000) == 0b1000000) tmp.add("SA");
                rule += String.join(",",tmp) + ";";
            }
        }
        if(expire != null)
        {
            rule += "UNTIL=" + String.format("%04d%02d%02d", expire.getYear(), expire.getMonthValue(), expire.getDayOfMonth()) +"T000000Z;";
            Logging.info(CalendarConverter.class, rule);
        }
        recurrence.add(rule);
        return recurrence;
    }
}
