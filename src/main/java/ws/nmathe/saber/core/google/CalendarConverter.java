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
import ws.nmathe.saber.core.schedule.EventRecurrence;
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

    public void init()
    {
        try
        {
            // Build a new authorized API client service.
            // Note: Do not confuse this class with the
            //   com.google.api.services.calendar.model.Calendar class.
            // if this fails, do not enable schedule synchronization
            Calendar service = GoogleAuth.getCalendarService(GoogleAuth.authorize());

            Main.getCommandHandler().putSync(); // enable the sync command
            Main.getScheduleManager().init(); // start the schedule sync timer
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
     * @param service connected calendar service with user credentials
     * @return (boolean) true if valid
     */
    public boolean checkValidAddress(String address, Calendar service)
    {
        try
        {
            service.events().list(address)
                    .setTimeMin(new DateTime(ZonedDateTime.now().format(EventRecurrence.RFC3339_FORMATTER)))
                    .setTimeMax(new DateTime(ZonedDateTime.now().plusDays(7).format(EventRecurrence.RFC3339_FORMATTER)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(Main.getBotSettingsManager().getMaxEntries())
                    .execute();

            return true;
        }
        catch(Exception e)
        {
            return false;
        }

    }


    /**
     * exports a discord schedule to a google calendar Calendar
     * @param address (String) valid address of calendar
     * @param channel (MessageChannel) channel to sync with
     * @param service connected calendar service with user credentials
     * @return boolean indicating if the export was successful
     */
    public boolean exportCalendar(String address, TextChannel channel, Calendar service)
    {
        if(channel == null || address == null) return false;
        if(!Main.getScheduleManager().isSchedule(channel.getId()))
        {   // safety check to insure exportCalendar is being applied to a valid channel
            return false;
        }

        Integer failure[] = { 0 };
        Collection<ScheduleEntry> entries = Main.getEntryManager().getEntriesFromChannel(channel.getId());
        entries.forEach(se->
        {
            // compose the event's description
            String description = String.join("\n", se.getComments())+"\n";
            if (se.getImageUrl() != null)     description += "\nimage: " + se.getImageUrl();
            if (se.getThumbnailUrl() != null) description += "\nthumbnail: " + se.getThumbnailUrl();
            if (se.getDeadline() != null)     description += "\ndeadline: " + se.getDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (se.getTitleUrl() != null)     description += "\nurl: " + se.getTitleUrl();
            for (String key : se.getRsvpLimits().keySet())
            {
                description += "\nlimit: " + key + " " + se.getRsvpLimit(key);
            }

            // setup the event's start and end times
            ZoneId zone = Main.getScheduleManager().getTimeZone(channel.getId());
            EventDateTime start = new EventDateTime()
                    .setDateTime(new DateTime(Date.from(se.getStart().toInstant())))
                    .setTimeZone(zone.getId());
            EventDateTime end = new EventDateTime()
                    .setDateTime(new DateTime(Date.from(se.getEnd().toInstant())))
                    .setTimeZone(zone.getId());
            EventDateTime origStart = new EventDateTime()
                    .setDateTime(new DateTime(Date.from(se.getRecurrence().getOriginalStart().toInstant())))
                    .setTimeZone(zone.getId());

            // create the event
            Event event = new Event();
            event.setDescription(description)
                    .setSummary(se.getTitle())
                    .setRecurrence(se.getRecurrence().toRFC5545())
                    .setStart(start)
                    .setEnd(end)
                    .setOriginalStartTime(origStart);

            try // interface with google calendar api
            {
                boolean differentCalendars = false;
                if (Main.getScheduleManager().getAddress(channel.getId()).equalsIgnoreCase(address)) differentCalendars = true;
                if (se.getGoogleId() != null && differentCalendars)
                {
                    event.setId(se.getGoogleId());
                    service.events().update(address, se.getGoogleId(), event).execute();
                }
                else
                {
                    Event e = service.events().insert(address, event).execute();
                    Main.getEntryManager().updateEntry(se.setGoogleId(e.getId()), false);
                }
            }
            catch (Exception e)
            {
                Logging.warn(this.getClass(), "Unable to export calendar:" +e.getMessage());
                failure[0] = 1;
            }
        });
        return failure[0]==0;
    }


    /**
     * Purges a schedule from entries and adds events (after conversion)
     * from the next 7 day span of a Google Calendar
     * @param address (String) valid address of calendar
     * @param channel (MessageChannel) channel to sync with
     * @param service connected calendar service with user credentials
     */
    public void importCalendar(String address, TextChannel channel, Calendar service)
    {
        // sanity checks
        if(channel == null || address == null) return;
        if(!Main.getScheduleManager().isSchedule(channel.getId())) return;

        // query the google calendar address for the list of events
        Events events;
        try
        {
            ZonedDateTime min = ZonedDateTime.now();
            ZonedDateTime max = min.plusDays(Main.getScheduleManager().getSyncLength(channel.getId()));
            events = service.events().list(address)
                    .setTimeMin(new DateTime(min.format(EventRecurrence.RFC3339_FORMATTER)))
                    .setTimeMax(new DateTime(max.format(EventRecurrence.RFC3339_FORMATTER)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(Main.getBotSettingsManager().getMaxEntries())
                    .execute();
        }
        catch (Exception e)
        {
            Logging.exception(this.getClass(), e);
            return;
        }

        try // convert the list of Google Events into discord event entries
        {
            channel.sendTyping().queue(); // send 'is typing' while the sync is in progress

            /* lock the schedule for syncing; schedule is unlocked in finally block */
            Main.getScheduleManager().lock(channel.getId());

            // change the zone to match the calendar
            // only if the zone has not been manually set for that schedule
            ZoneId zone = ZoneId.of( events.getTimeZone() );
            Boolean syncZone = Main.getDBDriver().getScheduleCollection().find(eq("_id", channel.getId()))
                    .first().getBoolean("timezone_sync", false);
            if(syncZone)
            {
                Main.getScheduleManager().setTimeZone( channel.getId(), zone );
            }

            // a set of all unique (not child of a recurring event) events
            HashSet<String> uniqueEvents = new HashSet<>();

            // process events
            for(Event event : events.getItems())
            {
                channel.sendTyping().queue();   // continue to send 'is typing'

                // if the unique google event ID does not appear in the already processed events
                // convert the event and add it to the schedule
                String recurrenceId = event.getRecurringEventId();
                String googleId = recurrenceId==null ? event.getId() : recurrenceId;
                if(!uniqueEvents.contains(googleId))
                {
                    // declare and initialize event parameters
                    ZonedDateTime start, end;
                    String title;
                    ArrayList<String> comments      = new ArrayList<>();
                    int repeat                      = 0;
                    ZonedDateTime expire            = null;
                    String imageUrl                 = null;
                    String thumbnailUrl             = null;
                    ZonedDateTime rsvpDeadline      = null;
                    String titleUrl                 = null;
                    Map<String, Integer> rsvpLimits = new HashMap<>();


                    if(event.getStart().getDateTime() == null)
                    {   /* parse start and end dates for all day events */
                        start = ZonedDateTime.of(
                                LocalDate.parse(event.getStart().getDate().toStringRfc3339()),
                                LocalTime.MIN,
                                zone);
                        end = ZonedDateTime.of(
                                LocalDate.parse(event.getEnd().getDate().toStringRfc3339()),
                                LocalTime.MIN,
                                zone);
                    } else
                    {   /* parse start and end times for normal events */
                        start = ZonedDateTime.parse(event.getStart().getDateTime().toStringRfc3339(), EventRecurrence.RFC3339_FORMATTER)
                                .withZoneSameInstant(zone);
                        end = ZonedDateTime.parse(event.getEnd().getDateTime().toStringRfc3339(), EventRecurrence.RFC3339_FORMATTER)
                                .withZoneSameInstant(zone);
                    }

                    // get event title
                    if(event.getSummary() == null) title = "(No title)";
                    else title = event.getSummary();

                    // process event description into event comments or other settings
                    if (event.getDescription() != null)
                    {
                        // process the description line by line
                        String description = HTMLStripper.cleanDescription(
                                event.getDescription().replace("\n", "<br>"));
                        for (String comment : description.split("\n"))
                        {
                            comment = comment.trim();
                            String lowerCase = comment.toLowerCase();

                            // image
                            if (lowerCase.startsWith("image:"))
                            {
                                String[] tmp = comment.split(":",2); // split to limit:
                                if(tmp.length > 1)
                                {
                                    imageUrl = tmp[1].trim().replaceAll(" ","");
                                    if (!VerifyUtilities.verifyUrl(imageUrl)) imageUrl = null;
                                }
                            }
                            // thumbnail
                            else if (lowerCase.startsWith("thumbnail:"))
                            {
                                String[] tmp = comment.split(":",2);
                                if(tmp.length > 1)
                                {
                                    thumbnailUrl = tmp[1].trim().trim().replaceAll(" ","");
                                    if(!VerifyUtilities.verifyUrl(thumbnailUrl)) thumbnailUrl = null;
                                }
                            }
                            // limit
                            else if (lowerCase.startsWith("limit:"))
                            {
                                String[] tmp = comment.split(":",2); // split to limit:
                                if(tmp.length > 1)
                                {
                                    String[] str = tmp[1].trim().split("[^\\S\n\r]+"); // split into white space separated segments
                                    if(str.length >= 2)
                                    {
                                        // rebuild the rsvp group name
                                        StringBuilder name = new StringBuilder();
                                        for(int i=0; i<str.length-1; i++)
                                        {
                                            name.append(str[i]);
                                            if(i != str.length-2) name.append(" ");
                                        }

                                        // parse the limit
                                        Integer limit = -1;
                                        if(VerifyUtilities.verifyInteger(str[str.length-1]))
                                            limit = Integer.parseInt(str[str.length-1]);

                                        rsvpLimits.put(name.toString(), limit);
                                    }
                                }

                            }
                            // title url
                            else if (lowerCase.startsWith("url:"))
                            {
                                String[] tmp = comment.split(":",2);
                                if(tmp.length > 1 && VerifyUtilities.verifyUrl(tmp[1].trim().replaceAll(" ","")))
                                    titleUrl = tmp[1].trim().replaceAll(" ","");
                            }
                            // deadline
                            else if (lowerCase.startsWith("deadline:"))
                            {
                                String tmp = lowerCase.replace("deadline:","")
                                        .trim().replaceAll(" ","");
                                if(VerifyUtilities.verifyDate(tmp))
                                    rsvpDeadline = ZonedDateTime.of(ParsingUtilities
                                            .parseDate(tmp, zone), LocalTime.MAX, zone);
                            }
                            // plaintext comment
                            else if(!comment.trim().isEmpty())
                            {
                                comments.add(comment);
                            }
                        }
                    }

                    // get the event recurrence information
                    List<String> recurrence = event.getRecurrence();
                    if(recurrenceId != null)
                        recurrence = service.events().get(address, recurrenceId).execute().getRecurrence();

                    // parse the event recurrence information
                    if(recurrence != null)
                    {   // determine the start date
                        ZonedDateTime dtStart = event.getOriginalStartTime() == null ? start :     // if orig is null, use start
                                    (event.getOriginalStartTime().getDateTime() == null ? start :
                                        ZonedDateTime.parse(event.getOriginalStartTime()
                                                .getDateTime().toStringRfc3339(), EventRecurrence.RFC3339_FORMATTER)
                                                .withZoneSameInstant(zone));
                        EventRecurrence eventRecurrence = new EventRecurrence(recurrence, dtStart);
                        expire = eventRecurrence.getExpire();
                        repeat = eventRecurrence.getRepeat();
                    }

                    // if the google event already exists as a saber event on the schedule, update it
                    // otherwise add as a new saber event
                    Document doc = Main.getDBDriver().getEventCollection()
                            .find(and(
                                    eq("channelId", channel.getId()),
                                    eq("googleId", googleId))).first();

                    // should the event be flagged as already started?
                    boolean hasStarted = start.isBefore(ZonedDateTime.now());

                    if(doc != null && (new ScheduleEntry(doc)).getMessageObject() != null)
                    {   /* update an existing event */
                        ScheduleEntry se = (new ScheduleEntry(doc))
                                .setTitle(title)
                                .setStart(start)
                                .setEnd(end)
                                .setRepeat(repeat)
                                .setGoogleId(googleId)
                                .setExpire(expire)
                                .setStarted(hasStarted)
                                .setComments(comments)
                                .setLocation(event.getLocation());

                        // set special attributes if not null
                        if (titleUrl!=null)
                            se.setTitleUrl(titleUrl);
                        if (imageUrl!=null)
                            se.setImageUrl(imageUrl);
                        if (thumbnailUrl!=null)
                            se.setThumbnailUrl(thumbnailUrl);
                        if (rsvpDeadline!=null)
                            se.setRsvpDeadline(rsvpDeadline);
                        if (rsvpLimits.keySet().size()>0)
                            se.setRsvpLimits(rsvpLimits);

                        // update event reminders using schedule default settings
                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                                .reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();

                        Main.getEntryManager().updateEntry(se, false);
                    }
                    else
                    {   /* create a new event */
                        ScheduleEntry se = (new ScheduleEntry(channel, title, start, end))
                                .setTitleUrl(titleUrl!=null ? titleUrl:event.getHtmlLink())
                                .setRepeat(repeat)
                                .setGoogleId(googleId)
                                .setExpire(expire)
                                .setStarted(hasStarted)
                                .setComments(comments)
                                .setLocation(event.getLocation());

                        // set special attributes if not null
                        if (imageUrl!=null)
                            se.setImageUrl(imageUrl);
                        if (thumbnailUrl!=null)
                            se.setThumbnailUrl(thumbnailUrl);
                        if (rsvpDeadline!=null)
                            se.setRsvpDeadline(rsvpDeadline);
                        if (rsvpLimits.keySet().size()>0)
                            se.setRsvpLimits(rsvpLimits);

                        Main.getEntryManager().newEntry(se, false);
                    }

                    // add to google ID to unique event mapping
                    uniqueEvents.add(recurrenceId==null ? event.getId() : recurrenceId);
                }
            }

            // purge channel of all entries on schedule that aren't in uniqueEvents
            Bson query = and(   eq("channelId", channel.getId()),
                                nin("googleId", uniqueEvents));
            Main.getDBDriver().getEventCollection().find(query)
                    .forEach((Consumer<? super Document>) document ->
                    {
                        ScheduleEntry entry = Main.getEntryManager().getEntry((Integer) document.get("_id"));
                        Message msg = entry.getMessageObject();
                        if( msg==null ) return;

                        Main.getEntryManager().removeEntry((Integer) document.get("_id"));
                        MessageUtilities.deleteMsg(msg, null);
                    });

            // set channel topic
            JDA jda = Main.getShardManager().getJDA(channel.getGuild().getId());
            String calLink = "https://calendar.google.com/calendar/embed?src=" + address;
            boolean hasPerms = channel.getGuild().getMember(jda.getSelfUser())
                    .hasPermission(channel, Permission.MANAGE_CHANNEL);
            if(hasPerms) channel.getManager().setTopic(calLink).queue();
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
}
