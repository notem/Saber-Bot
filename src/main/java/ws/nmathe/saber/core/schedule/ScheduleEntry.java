package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.utils.Logging;

import javax.xml.soap.Text;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * A ScheduleEntry object represents a currently scheduled entry is either waiting to start or has already started
 * start and end functions are to be triggered upon the scheduled starting time and ending time.
 * reloadDisplay is used to update the displayed 'time until' timer
 */
public class ScheduleEntry
{
    // identifiers
    private Integer entryId;                      // 16 bit identifier
    private String msgId;
    private String chanId;
    private String guildId;
    private String googleId;

    // entry parameters
    private String entryTitle;                    // the title/name of the event
    private ZonedDateTime entryStart;             // the time when the event starts
    private ZonedDateTime entryEnd;               // the ending time
    private ArrayList<String> entryComments;      // ArrayList of strings that make up the desc
    private Integer entryRepeat;
    private List<Date> reminders;
    private List<Date> endReminders;

    // rsvp
    private Map<String, List<String>> rsvpMembers;
    private Map<String, Integer> rsvpLimits;
    private ZonedDateTime rsvpDeadline;

    // urls
    private String titleUrl;
    private String imageUrl;
    private String thumbnailUrl;

    // toggles
    private boolean quietStart;
    private boolean quietEnd;
    private boolean quietRemind;

    // misc
    private boolean hasStarted;
    private ZonedDateTime expire;

    // announcement overrides
    // these hold temporary values
    private Set<Date> announcements;                   // used by DB for queries
    private Map<String, Date> announcementDates;       // maps Date to IDs
    // these are constant across events in a series
    private Map<String, String> announcementTimes;     // maps ID to rel-time string
    private Map<String, String> announcementTargets;   // maps ID to channel target
    private Map<String, String> announcementMessages;  // maps ID to announcement message


    /**
     * Constructor for a partially initialized ScheduleEntry
     * @param channel (TextChannel) the schedule channel
     * @param title (String) event title
     * @param start (ZonedDateTime) time to start event
     * @param end (ZonedDateTime) time to end event
     */
    public ScheduleEntry(TextChannel channel, String title, ZonedDateTime start, ZonedDateTime end)
    {
        // identifiers
        this.entryId = null;
        this.msgId = null;
        this.chanId = channel.getId();
        this.guildId = channel.getGuild().getId();

        // entry parameters
        this.entryTitle = title;
        this.entryStart = start;
        this.entryEnd = end;
        this.entryRepeat = 0;
        this.entryComments = new ArrayList<>();

        // rsvp
        this.rsvpMembers = new LinkedHashMap<>();
        this.rsvpLimits = new LinkedHashMap<>();
        this.rsvpDeadline = null;

        // toggles
        this.quietStart = false;
        this.quietEnd = false;
        this.quietRemind = false;

        // urls
        this.titleUrl = null;
        this.imageUrl = null;
        this.thumbnailUrl = null;

        // misc
        this.hasStarted = false;
        this.expire = null;

        // announcement overrides
        this.announcements = new HashSet<>();
        this.announcementDates= new HashMap<>();
        this.announcementTimes = new HashMap<>();
        this.announcementTargets = new HashMap<>();
        this.announcementMessages = new HashMap<>();
    }


    /**
     * Constructor for a fully initialized ScheduleEntry
     * @param entryDocument (Document) taken from the events collection in the database backing the bot
     */
    @SuppressWarnings("unchecked")
    public ScheduleEntry(Document entryDocument)
    {
        // identifiers
        this.entryId = entryDocument.getInteger("_id");
        this.msgId = (String) entryDocument.get("messageId");
        this.chanId = (String) entryDocument.get("channelId");
        this.guildId = (String) entryDocument.get("guildId");
        this.googleId = (String) entryDocument.get("googleId");

        ZoneId zone = Main.getScheduleManager().getTimeZone(this.chanId);

        // entry parameters
        this.entryTitle = entryDocument.getString("title");
        this.entryStart = ZonedDateTime.ofInstant((entryDocument.getDate("start")).toInstant(), zone);
        this.entryEnd = ZonedDateTime.ofInstant((entryDocument.getDate("end")).toInstant(), zone);
        this.entryComments = (ArrayList<String>) entryDocument.get("comments");
        this.entryRepeat = entryDocument.getInteger("repeat");

        // reminders
        this.reminders = entryDocument.get("reminders")!=null ?
                (List<Date>) entryDocument.get("reminders") : new ArrayList<>();
        this.endReminders = entryDocument.get("end_reminders")!=null ?
                (List<Date>) entryDocument.get("end_reminders") : new ArrayList<>();

        // rsvp
        this.rsvpMembers = (Map) (entryDocument.get("rsvp_members") == null ?
                new LinkedHashMap<>() : entryDocument.get("rsvp_members"));
        this.rsvpLimits = (Map) (entryDocument.get("rsvp_limits") == null ?
                new LinkedHashMap<>() : entryDocument.get("rsvp_limits"));
        this.rsvpDeadline = entryDocument.get("deadline") == null ?
                null : ZonedDateTime.ofInstant(entryDocument.getDate("deadline").toInstant(), zone);

        // urls
        this.titleUrl = entryDocument.getString("url");
        this.imageUrl = entryDocument.getString("image");
        this.thumbnailUrl = entryDocument.getString("thumbnail");

        // toggles
        this.quietStart = (boolean) (entryDocument.get("start_disabled") != null ?
                entryDocument.get("start_disabled") : false);
        this.quietEnd = (boolean) (entryDocument.get("end_disabled") != null ?
                entryDocument.get("end_disabled") : false);
        this.quietRemind = (boolean) (entryDocument.get("reminders_disabled") != null ?
                entryDocument.get("reminders_disabled") : false);

        // misc
        this.hasStarted = (boolean) entryDocument.get("hasStarted");
        this.expire = entryDocument.get("expire") == null ?
                null : ZonedDateTime.ofInstant(entryDocument.getDate("expire").toInstant(), zone);

        // announcement overrides
        this.announcements = entryDocument.get("announcements") == null ?
                new HashSet<>() : new HashSet<>(((List<Date>) entryDocument.get("announcements")));
        this.announcementDates = entryDocument.get("announcement_dates") == null ?
                new HashMap<>() : (Map<String, Date>) entryDocument.get("announcement_dates");
        this.announcementTimes = entryDocument.get("announcement_times") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_times");
        this.announcementTargets = entryDocument.get("announcement_targets") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_targets");
        this.announcementMessages = entryDocument.get("announcement_messages") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_messages");
    }

    /**
     * handles sending special announcements
     */
    public void announce()
    {
        Message msg = this.getMessageObject();
        if(msg == null) return;         // if msg object is bad

        // collection of announcement IDs to be removed below enumeration
        Collection<String> removeQueue = new ArrayList<>();

        // enumerate over all dates in the past
        this.announcements.stream().filter(date-> date.before(new Date())).forEach(date->
        {
            for(String key : this.announcementTimes.keySet())
            {
                // for each announcement ID that scheduled for the date, send announcement
                if(this.announcementDates.get(key).equals(date))
                {
                    String message = ParsingUtilities.parseMessageFormat(this.announcementMessages.get(key), this);
                    String target = this.announcementTargets.get(key);
                    try
                    {
                        announcementHelper(msg, message, target);
                        Logging.event(this.getClass(), "Sent special announcement for event " +
                                this.getTitle() + " [" + this.getId() + "]");
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                    removeQueue.add(key);
                }
            }
        });

        // remove all the processed announcements and update event
        removeQueue.forEach(key->
        {
            Date date = this.announcementDates.get(key);
            this.announcements.remove(date);
            if(!this.announcementDates.containsValue(date))
            {
                this.announcements.remove(date);
            }
        });
        Main.getEntryManager().updateEntry(this, false);
    }

    /**
     * handles sending reminder notifications
     */
    public void remind()
    {
        Message msg = this.getMessageObject();
        if(msg == null) return;         // if msg object is bad

        if(!this.quietRemind)
        {
            // parse message and get the target channels
            String remindMsg = ParsingUtilities.parseMessageFormat(Main.getScheduleManager().getReminderFormat(this.chanId), this);
            String identifier = Main.getScheduleManager().getReminderChan(this.chanId);
            if(identifier != null)
            {
                announcementHelper(msg, remindMsg, identifier);
                Logging.event(this.getClass(), "Sent reminder for event " + this.getTitle() + " [" + this.getId() + "]");
            }
        }

        // remove expired reminders
        this.reminders.removeIf(date -> date.before(new Date()));
        this.endReminders.removeIf(date -> date.before(new Date()));
        Main.getEntryManager().updateEntry(this, false);
    }

    /**
     * Handles when an event begins
     */
    public void start()
    {
        Message msg = this.getMessageObject();
        if( msg == null ) return;

        if(!this.quietStart)
        {
            // dont send start announcements if 15 minutes late
            if(this.entryStart.isAfter(ZonedDateTime.now().minusMinutes(15)))
            {
                String startMsg = ParsingUtilities.parseMessageFormat(Main.getScheduleManager().getStartAnnounceFormat(this.chanId), this);
                String identifier = Main.getScheduleManager().getStartAnnounceChan(this.chanId);
                if(identifier != null)
                {
                    announcementHelper(msg, startMsg, identifier);
                    Logging.event(this.getClass(), "Started event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                            this.getStart().withZoneSameInstant(ZoneId.systemDefault())
                                    .truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString());
                }
            }
            else
            {
                Logging.warn(this.getClass(), "Late event start: "+this.entryTitle+" ["+this.entryId+"] "+this.entryStart);
            }
        }

        // if the entry's start time is the same as it's end
        // skip to end
        if(this.entryStart.isEqual(this.entryEnd))
        {
            this.repeat();
        }
        else
        {
            this.reloadDisplay();
            this.hasStarted = true;
            Main.getDBDriver().getEventCollection().updateOne(eq("_id", this.entryId), set("hasStarted", true));
        }
    }

    /**
     * handles when an event ends
     */
    public void end()
    {
        Message msg = this.getMessageObject();
        if(msg == null) return;

        if(!this.quietEnd)
        {
            // dont send end announcement if 15 minutes late
            if(this.entryEnd.isAfter(ZonedDateTime.now().minusMinutes(15)))
            {
                // send the end announcement
                String endMsg = ParsingUtilities.parseMessageFormat(Main.getScheduleManager().getEndAnnounceFormat(this.chanId), this);
                String identifier = Main.getScheduleManager().getEndAnnounceChan(this.chanId);
                if(identifier != null)
                {
                    announcementHelper(msg, endMsg, identifier);
                    Logging.event(this.getClass(), "Ended event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                            this.getEnd().withZoneSameInstant(ZoneId.systemDefault())
                                    .truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString());
                }
            }
        }
        else
        {
            Logging.warn(this.getClass(), "Late event end: "+this.entryTitle+" ["+this.entryId+"] "+this.entryEnd);
        }

        this.repeat();
    }

    /**
     * processes a channel identifier (either a channel name or snowflake ID) into a valid channel
     * and sends an event announcement
     */
    private void announcementHelper(Message message, String content, String channelIdentifier)
    {
        boolean success = false;

        // if the identifier is all digits, attempt to treat the identifier as a snowflake ID
        if(channelIdentifier.matches("\\d+"))
        {
            try
            {
                TextChannel channel = message.getGuild().getTextChannelById(channelIdentifier);
                if(channel != null)
                {
                    MessageUtilities.sendMsg(content, channel, null);
                    success = true;
                }
            }
            catch(Exception ignored)
            {}
        }
        // if the announcement has not sent using the identifier as a snowflake,
        // treat the identifier as a channel name
        if(!success)
        {
            List<TextChannel> channels = message.getGuild().getTextChannelsByName(channelIdentifier, true);
            for( TextChannel chan : channels )
            {
                MessageUtilities.sendMsg(content, chan, null);
            }
        }
    }


    /**
     * Determines what needs to be done to an event when an event ends
     */
    public void repeat()
    {
        Message msg = this.getMessageObject();
        if( msg==null ) return;

        if(this.entryRepeat != 0) // find next repeat date and edit the message
        {
            this.setNextOccurrence().setStarted(false);

            // if the next time an event repeats is after the event's expire, delete the event
            if(this.expire != null && this.expire.isBefore(this.getStart()))
            {
                Main.getEntryManager().removeEntry(this.entryId);
                MessageUtilities.deleteMsg( msg, null );
                return;
            }

            // recreate the announcement overrides
            this.regenerateAnnouncementOverrides();

            // clear rsvp memberes list and reload reminders
            this.rsvpMembers = new HashMap<>();
            this.reloadReminders(Main.getScheduleManager().getReminders(this.chanId))
                    .reloadEndReminders(Main.getScheduleManager().getEndReminders(this.chanId));
            Main.getEntryManager().updateEntry(this, true);
        }
        else // otherwise remove entry and delete the message
        {
            Main.getEntryManager().removeEntry(this.entryId);
            MessageUtilities.deleteMsg( msg, null );
        }
    }


    /**
     * Edits the displayed Message to indicate the time remaining until
     * the entry is scheduled to begin/end
     */
    void reloadDisplay()
    {
        Message msg = this.getMessageObject();
        if( msg == null ) return;
        MessageUtilities.editMsg(MessageGenerator.generate(this), msg, null);
    }


    /**
     * Bit designations:
     * 1-7 : repeat on specific weekdays
     * 8   : repeat on day interval
     * 9   : repeat yearly
     * 12  : repeat every [x] minutes
     * The schedule object's start and end zoneddatetimes are updated accordingly
     * @return (ScheduleEntry) this object
     */
    private ScheduleEntry setNextOccurrence()
    {
        // 12th bit denotes minute repeat
        if((this.entryRepeat & 0b100000000000) == 0b100000000000)
        {
            int masked = this.entryRepeat & 0b011111111111;
            this.entryStart = entryStart.plusMinutes(masked);
            this.entryEnd = entryEnd.plusMinutes(masked);
            return this;
        }
        // yearly repeat (9th bit)
        if((this.entryRepeat & 0b100000000) == 0b100000000)
        {
            this.entryStart = entryStart.plusYears(1);
            this.entryEnd = entryEnd.plusYears(1);
            return this;
        }
        // determine the number of days until the next scheduled occurrence
        // and update the start and end by adding the appropriate number of days
        else
        {
            int days;

            // repeat on daily interval (8th bit)
            if((this.entryRepeat & 0b10000000) == 0b10000000)
            {
                days = this.entryRepeat ^ 0b10000000;
            }
            else // repeat on weekday schedule
            {
                // convert to current day of week to binary representation
                int dayOfWeek = entryStart.getDayOfWeek().getValue();
                int dayAsBitSet;
                if( dayOfWeek == 7 ) //sunday
                {
                    dayAsBitSet = 1;
                }
                else                //monday - saturday
                {
                    dayAsBitSet = 1<<dayOfWeek;
                }

                // if repeats on same weekday next week
                if( (dayAsBitSet | this.entryRepeat) == dayAsBitSet )
                {
                    days = 7;
                }
                else
                {
                    // if the eighth bit is off, the event repeats on fixed days of the week (ie. on tuesday and wednesday)
                    int daysTil = 0;
                    for( int i = 1; i < 7; i++)
                    {
                        if( dayAsBitSet == 0b1000000 )      //if bitset is SATURDAY, then
                        {
                            dayAsBitSet = 0b0000001;        //set bitset to SUNDAY
                        }
                        else
                        {
                            dayAsBitSet <<= 1;     // else, set to the next day
                        }

                        if( (dayAsBitSet & this.entryRepeat) == dayAsBitSet )
                        {
                            daysTil = i;
                            break;
                        }
                    }
                    days = daysTil; // if this is zero, eRepeat was zero
                }
            }

            // update the entry's start and end
            // check to insure that the year is incremented when the addition caused wrapping
            this.entryStart = this.entryStart.plusDays(days).isAfter(this.entryStart) ?
                    this.entryStart.plusDays(days) : this.entryStart.plusDays(days).plusYears(1);
            this.entryEnd = this.entryEnd.plusDays(days).isAfter(this.entryEnd) ?
                    this.entryEnd.plusDays(days) : this.entryEnd.plusDays(days).plusYears(1);
            return this;
        }
    }

    /*
     * getters
     */

    /**
     * true if the event has been started
     */
    public boolean hasStarted()
    {
        return this.hasStarted;
    }

    /**
     * true if the event can accept no more members for an rsvp category
     */
    public boolean isFull(String type)
    {
        Integer limit = this.rsvpLimits.get(type)==null ? -1 : this.rsvpLimits.get(type);
        Integer size = this.rsvpMembers.get(type)==null ? 0 : this.rsvpMembers.get(type).size();
        return (limit > -1) && (size >= limit);
    }

    /**
     * retrieves the event's title
     */
    public String getTitle()
    {
        return this.entryTitle;
    }

    /**
     * retrieves the event's start datetime
     */
    public ZonedDateTime getStart()
    {
        return this.entryStart;
    }

    /**
     * retrieves the event's end datetime
     */
    public ZonedDateTime getEnd()
    {
        return this.entryEnd;
    }

    /**
     * retrieves the event's comments
     */
    public ArrayList<String> getComments()
    {
        return this.entryComments;
    }

    /**
     * retrieves the event's unique ID
     */
    public Integer getId()
    {
        return this.entryId;
    }

    /**
     * retrieves the event's repeat settings
     */
    public Integer getRepeat()
    {
        return this.entryRepeat;
    }

    /**
     * retrieves the event's title url
     */
    public String getTitleUrl()
    {
        return this.titleUrl;
    }

    /**
     * retrieves the event's reminders
     */
    public List<Date> getReminders()
    {
        return this.reminders;
    }

    /**
     * retrieves the event's end reminders
     */
    public List<Date> getEndReminders()
    {
        return this.endReminders;
    }

    /**
     * retrieves the event's google ID
     */
    public String getGoogleId()
    {
        return this.googleId;
    }

    /**
     * retrieves an rsvp category's limit
     */
    public Integer getRsvpLimit(String type)
    {
        Integer limit = this.rsvpLimits.get(type);
        if(limit == null)
        {
            return -1;
        }
        return limit;
    }

    /**
     * retrieves an rsvp category's list of members
     */
    public List<String> getRsvpMembersOfType(String type)
    {
        List<String> members = this.rsvpMembers.get(type);
        if(members == null)
        {
            return new ArrayList<>();
        }
        return members;
    }

    /**
     * retrieves full map of rsvp member
     */
    public Map<String, List<String>> getRsvpMembers()
    {
        return this.rsvpMembers;
    }

    /**
     * retrieves full map of rsvp limits
     */
    public Map<String, Integer> getRsvpLimits()
    {
        return this.rsvpLimits;
    }

    /**
     * retrieves the datetime the event is set to expire or null
     */
    public ZonedDateTime getExpire()
    {
        return this.expire;
    }


    /**
     * should the event send start announcements?
     */
    public boolean isQuietStart()
    {
        return this.quietStart;
    }

    /**
     * should the event send end announcements?
     */
    public boolean isQuietEnd()
    {
        return this.quietEnd;
    }

    /**
     * should the event reminders?
     */
    public boolean isQuietRemind()
    {
        return this.quietRemind;
    }

    /**
     * retrieves the event's image url
     */
    public String getImageUrl()
    {
        return this.imageUrl;
    }

    /**
     * retrieves the event's thumbnail url
     */
    public String getThumbnailUrl()
    {
        return this.thumbnailUrl;
    }

    /**
     * retrieves the event's guild ID url
     */
    public String getGuildId()
    {
        return this.guildId;
    }

    /**
     * retrieves the event's channel ID url
     */
    public String getChannelId()
    {
        return this.chanId;
    }

    /**
     * retrieves the event's rsvp deadline
     */
    public ZonedDateTime getDeadline()
    {
        return this.rsvpDeadline;
    }

    /**
     * retrieves the full map of announcement override relative times
     */
    public Map<String, String> getAnnouncementTimes()
    {
        return this.announcementTimes;
    }

    /**
     * retrieves the full map of announcement override target channels
     */
    public Map<String, String> getAnnouncementTargets()
    {
        return this.announcementTargets;
    }

    /**
     * retrieves the full map of announcement override message format strings
     */
    public Map<String, String> getAnnouncementMessages()
    {
        return this.announcementMessages;
    }

    /**
     * retrieves the full map of announcement override active date object
     */
    public Map<String, Date> getAnnouncementDates()
    {
        return this.announcementDates;
    }

    /**
     * retrieves the full list of active date announcement objects
     */
    public Set<Date> getAnnouncements()
    {
        return this.announcements;
    }

    /**
     * Attempts to retrieve the discord Message, if the message does not exist
     * (or the bot can for any other reason cannot retrieve it) the method returns null
     * @return (Message) if exists, otherwise null
     */
    public Message getMessageObject()
    {
        Message msg;
        try
        {
            JDA jda = Main.getShardManager().isSharding() ? Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();

            msg = jda.getTextChannelById(this.chanId)
                    .getMessageById(this.msgId)
                    .complete();
        }
        catch( Exception e )
        {
            msg = null;
        }
        return msg;
    }

    /*
     * Setters
     */

    /**
     * sets the entry object's title
     */
    public ScheduleEntry setTitle(String title)
    {
        this.entryTitle = title;
        return this;
    }

    /**
     * sets the entry object's start datetime
     */
    public ScheduleEntry setStart(ZonedDateTime start)
    {
        if(this.entryStart.equals(this.entryEnd))
        {
            // if the event's end is 'off', update the end to match start
            this.entryEnd = start;
        }
        this.entryStart = start;
        return this;
    }

    /**
     * sets the entry object's end datetime
     */
    public ScheduleEntry setEnd(ZonedDateTime end)
    {
        this.entryEnd = end;
        return this;
    }

    /**
     * sets the entry object's array of comments
     */
    public ScheduleEntry setComments(ArrayList<String> comments)
    {
        this.entryComments = comments;
        return this;
    }

    /**
     * sets the entry object's repeat settings
     */
    public ScheduleEntry setRepeat(Integer repeat)
    {
        this.entryRepeat = repeat;
        return this;
    }

    /**
     * sets the entry object's title url
     */
    public ScheduleEntry setTitleUrl(String url)
    {
        this.titleUrl = url;
        return this;
    }

    /**
     * regenerates entry reminders from schedule settings
     */
    public ScheduleEntry reloadReminders(List<Integer> reminders)
    {
        // generate event reminders from schedule settings
        List<Date> startReminders = new ArrayList<>();
        for(Integer til : reminders)
        {
            if(Instant.now().until(this.getStart(), ChronoUnit.MINUTES) >= til)
            {
                startReminders.add(Date.from(this.getStart().toInstant().minusSeconds(til*60)));
            }
        }
        this.reminders = startReminders;
        return this;
    }

    /**
     * regenerates entry end-reminders from schedule settings
     */
    public ScheduleEntry reloadEndReminders(List<Integer> reminders)
    {
        // generate end reminders
        List<Date> endReminders = new ArrayList<>();
        for(Integer til : reminders)
        {
            if(Instant.now().until(this.getEnd(), ChronoUnit.MINUTES) >= til)
            {
                endReminders.add(Date.from(this.getEnd().toInstant().minusSeconds(til*60)));
            }
        }
        this.endReminders = endReminders;
        return this;
    }

    /**
     * set's the entry's google event ID
     */
    public ScheduleEntry setGoogleId(String id)
    {
        this.googleId = id;
        return this;
    }

    /**
     * set's the entry's expire date
     */
    public ScheduleEntry setExpire(ZonedDateTime expire)
    {
        this.expire = expire;
        return this;
    }

    /**
     * set's the entry's main image
     */
    public ScheduleEntry setImageUrl(String url)
    {
        this.imageUrl = url;
        return this;
    }

    /**
     * set's the entry's thumbnail image
     */
    public ScheduleEntry setThumbnailUrl(String url)
    {
        this.thumbnailUrl = url;
        return this;
    }

    /**
     * quiet the event's start announcement
     */
    public ScheduleEntry setQuietStart(boolean bool)
    {
        this.quietStart = bool;
        return this;
    }

    /**
     * quiet the event's end announcement
     */
    public ScheduleEntry setQuietEnd(boolean bool)
    {
        this.quietEnd = bool;
        return this;
    }

    /**
     * quiet the event's reminders
     */
    public ScheduleEntry setQuietRemind(boolean bool)
    {
        this.quietRemind = bool;
        return this;
    }

    /**
     * flag the event as having been started
     */
    public ScheduleEntry setStarted(boolean bool)
    {
        this.hasStarted = bool;
        return this;
    }

    /**
     * set the event's unique ID
     */
    public ScheduleEntry setId(Integer id)
    {
        this.entryId = id;
        return this;
    }

    /**
     * set the event's associated discord message object
     */
    public ScheduleEntry setMessageObject(Message msg)
    {
        this.chanId = msg.getChannel().getId();
        this.guildId = msg.getGuild().getId();
        this.msgId = msg.getId();
        return this;
    }

    /**
     * set an rsvp limit for the event
     */
    public ScheduleEntry setRsvpLimit(String type, Integer limit)
    {
        if(rsvpLimits.containsKey(type))
        {
            this.rsvpLimits.replace(type, limit);
        }
        else
        {
            this.rsvpLimits.put(type, limit);
        }
        return this;
    }

    /**
     * set all rsvp limits for the event
     */
    public ScheduleEntry setRsvpLimits(Map<String, Integer> limits)
    {
        this.rsvpLimits = limits;
        return this;
    }

    /**
     * set the full mapping of rsvp'ed members
     */
    public ScheduleEntry setRsvpMembers(String type, List<String> members)
    {
        if(this.rsvpMembers.containsKey(type))
        {
            this.rsvpMembers.replace(type, members);
        }
        else
        {
            this.rsvpMembers.put(type, members);
        }
        return this;
    }

    /**
     * set the deadline by which members must rsvp
     */
    public ScheduleEntry setRsvpDeadline(ZonedDateTime deadline)
    {
        this.rsvpDeadline = deadline;
        return this;
    }

    /**
     * creates a new announcement override for the event
     */
    public ScheduleEntry addAnnouncementOverride(String channelId, String timeString, String message)
    {
        // create a new event-local ID for the announcement
        Integer id = null;
        for (int i=0; i<Integer.MAX_VALUE; i++)
        {
            if (!this.announcementDates.keySet().contains(i+""))
            {
                id = i;
                break;
            }
        }
        if (id == null)
        {
            Logging.warn(this.getClass(), "Unable to generate an ID");
            return this;
        }

        // create hard date time object
        ZonedDateTime datetime = ParsingUtilities.parseTimeString(timeString, this);
        if (datetime == null)
        {
            Logging.warn(this.getClass(), "Unable to generate the time");
            return this;
        }

        if(datetime.isAfter(ZonedDateTime.now()))
        {
            // add to active announcement list
            this.announcements.add(Date.from(datetime.toInstant()));
            this.announcementDates.put(id.toString(), Date.from(datetime.toInstant()));
        }

        // create mappings
        this.announcementTimes.put(id.toString(), timeString);
        this.announcementTargets.put(id.toString(), channelId);
        this.announcementMessages.put(id.toString(), message);

        return this;
    }

    /**
     * removes an announcement override (if one exists)
     */
    public ScheduleEntry removeAnnouncementOverride(Integer id)
    {
        Date date = this.announcementDates.get(id.toString());
        if (date != null)
        {
            this.announcementDates.remove(id.toString());
            this.announcementMessages.remove(id.toString());
            this.announcementTimes.remove(id.toString());
            this.announcementTargets.remove(id.toString());

            // only remove from the announcements list if there are no other announcements scheduled for that time
            if(!this.announcementDates.containsValue(date))
            {
                this.announcements.remove(date);
            }
        }
        return this;
    }

    /**
     * repopulates the announcements set and announcementDates
     * based on values in announcementTimes, announcementMessages, and announcementTargets
     */
    public ScheduleEntry regenerateAnnouncementOverrides()
    {
        for(String key : this.announcementTimes.keySet())
        {
            // create hard date time object
            ZonedDateTime datetime = ParsingUtilities.parseTimeString(this.announcementTimes.get(key), this);
            if (datetime == null)
            {
                Logging.warn(this.getClass(), "Unable to generate the time");
            }
            else if (datetime.isAfter(ZonedDateTime.now()))
            {
                // add to lists
                this.announcements.add(Date.from(datetime.toInstant()));
                this.announcementDates.put(key, Date.from(datetime.toInstant()));
            }
        }
        return this;
    }

    /**
     * creates string display for event comments
     */
    public String commentsToString()
    {
        String body = "// Comments\n";
        for(int i=1; i<this.getComments().size()+1; i++)
        {
            body += "[" + i + "] \"" + this.getComments().get(i-1) + "\"\n";
        }
        return body;
    }

    /**
     * creates string display for event limits
     */
    public String limitsToString()
    {
        String body = "// Limits\n";
        for(String key : this.getRsvpLimits().keySet())
        {
            body += key + " - " + this.getRsvpLimits().get(key) + "\n";
        }
        return body;
    }

    /**
     * creates string display for event announcements
     * @return
     */
    public String announcementsToString()
    {
        String body = "// Event Announcements\n";
        JDA jda = Main.getShardManager().getJDA(this.guildId);
        for (String id : this.announcementTimes.keySet())
        {
            TextChannel channel = jda.getTextChannelById(this.announcementTargets.get(id));
            body += "(" + (Integer.parseInt(id)+1) + ") \"" + this.announcementMessages.get(id)+ "\"" +
                    " at \"" + this.announcementTimes.get(id) + "\"" +
                    " to \"#" + (channel==null ? "unknown_channel" : channel.getName())+"\"\n";
        }
        return body;
    }

    @Override
    public String toString()
    {
        DateTimeFormatter dtf;
        if(Main.getScheduleManager().getClockFormat(this.getChannelId()).equals("24"))
        {
            dtf = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm [z]");
        }
        else
        {
            dtf = DateTimeFormatter.ofPattern("yyy-MM-dd hh:mma [z]");
        }

        String body = "```js\n" +
                "Title:  \"" + this.getTitle() + "\"\n" +
                "Start:  " + this.getStart().format(dtf) + "\n" +
                "End:    " + (this.getEnd().equals(this.getStart()) ? "\"off\"" : this.getEnd().format(dtf)) + "\n" +
                "Repeat: \"" + MessageGenerator.getRepeatString(this.getRepeat(), true) + "\"\n";

        // quiet settings
        if(this.isQuietRemind() | this.isQuietEnd() | this.isQuietStart())
        {
            body += "Quiet: ";
            if(this.isQuietStart())
            {
                body += "start";
                if(this.isQuietEnd() & this.isQuietRemind())
                {
                    body += ", ";
                }
                else if(this.isQuietEnd() | this.isQuietRemind())
                {
                    body += " and ";
                }
            }
            if(this.isQuietEnd())
            {
                body += "end";
                if(this.isQuietRemind())
                {
                    body += " and ";
                }
            }
            if(this.isQuietRemind())
            {
                body += "reminders";
            }
            body += " disabled\n";
        }

        // expire
        if(this.getExpire() != null)
        {
            body += "Expire: \"" + this.getExpire().toLocalDate() + "\"\n";
        }

        // title url
        if(this.getTitleUrl()!=null)
        {
            body += "Url: \n\"" + this.getTitleUrl() + "\"\n";
        }

        // image
        if(this.getImageUrl() != null)
        {
            body += "Image: \n\"" + this.getImageUrl() + "\"\n";
        }

        // thumbnail
        if(this.getThumbnailUrl() != null)
        {
            body += "Thumbnail: \n\"" + this.getThumbnailUrl() + "\"\n";
        }

        // rsvp limits
        if(Main.getScheduleManager().isRSVPEnabled(this.getChannelId()))
        {
            if(this.getDeadline() != null)
            {
                body += "Deadline: " + this.getDeadline().format(dtf) + "\n";
            }
            if(!this.getRsvpLimits().isEmpty())
            {
                body += "``````js\n" + this.limitsToString();
            }
        }

        // comments
        if(!this.getComments().isEmpty())
        {
            body += "``````js\n" + this.commentsToString();
        }

        // announcement overrides
        if(!this.announcementTimes.isEmpty())
        {
            body += "``````js\n" + this.announcementsToString();
        }
        return body + "\n```";
    }
}
