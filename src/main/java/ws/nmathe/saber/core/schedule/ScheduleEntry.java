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
        this.reminders = entryDocument.get("reminders")!=null ? (List<Date>) entryDocument.get("reminders") : new ArrayList<>();
        this.endReminders = entryDocument.get("end_reminders")!=null ? (List<Date>) entryDocument.get("end_reminders") : new ArrayList<>();

        // rsvp
        this.rsvpMembers = (Map) (entryDocument.get("rsvp_members")==null ? new LinkedHashMap<>() : entryDocument.get("rsvp_members"));
        this.rsvpLimits = (Map) (entryDocument.get("rsvp_limits")==null ? new LinkedHashMap<>() : entryDocument.get("rsvp_limits"));
        this.rsvpDeadline = entryDocument.get("deadline")==null ? null : ZonedDateTime.ofInstant(entryDocument.getDate("deadline").toInstant(), zone);

        // urls
        this.titleUrl = entryDocument.getString("url");
        this.imageUrl = entryDocument.getString("image");
        this.thumbnailUrl = entryDocument.getString("thumbnail");

        // toggles
        this.quietStart = (boolean) (entryDocument.get("start_disabled")!=null ?
                entryDocument.get("start_disabled") : false);
        this.quietEnd = (boolean) (entryDocument.get("end_disabled")!=null ?
                entryDocument.get("end_disabled") : false);
        this.quietRemind = (boolean) (entryDocument.get("reminders_disabled")!=null ?
                entryDocument.get("reminders_disabled") : false);

        // misc
        this.hasStarted = (boolean) entryDocument.get("hasStarted");
        this.expire = entryDocument.get("expire") == null ? null :
                ZonedDateTime.ofInstant(entryDocument.getDate("expire").toInstant(), zone);
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
    private void repeat()
    {
        Message msg = this.getMessageObject();
        if( msg==null ) return;

        if( this.entryRepeat != 0 ) // find next repeat date and edit the message
        {
            this.setNextOccurrence().setStarted(false);
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

    public boolean hasStarted()
    {
        return this.hasStarted;
    }

    public boolean isFull(String type)
    {
        Integer limit = this.rsvpLimits.get(type)==null ? -1 : this.rsvpLimits.get(type);
        Integer size = this.rsvpMembers.get(type)==null ? 0 : this.rsvpMembers.get(type).size();
        return (limit > -1) && (size >= limit);
    }

    public String getTitle()
    {
        return this.entryTitle;
    }

    public ZonedDateTime getStart()
    {
        return this.entryStart;
    }

    public ZonedDateTime getEnd()
    {
        return this.entryEnd;
    }

    public ArrayList<String> getComments()
    {
        return this.entryComments;
    }

    public Integer getId()
    {
        return this.entryId;
    }

    public Integer getRepeat()
    {
        return this.entryRepeat;
    }

    public String getTitleUrl()
    {
        return this.titleUrl;
    }

    public List<Date> getReminders()
    {
        return this.reminders;
    }

    public List<Date> getEndReminders()
    {
        return this.endReminders;
    }

    public String getGoogleId()
    {
        return this.googleId;
    }

    public Integer getRsvpLimit(String type)
    {
        Integer limit = this.rsvpLimits.get(type);
        if(limit == null)
        {
            return -1;
        }
        return limit;
    }

    public List<String> getRsvpMembersOfType(String type)
    {
        List<String> members = this.rsvpMembers.get(type);
        if(members == null)
        {
            return new ArrayList<>();
        }
        return members;
    }

    public Map<String, List<String>> getRsvpMembers()
    {
        return this.rsvpMembers;
    }

    public Map<String, Integer> getRsvpLimits()
    {
        return this.rsvpLimits;
    }

    public ZonedDateTime getExpire()
    {
        return this.expire;
    }

    public boolean isQuietStart()
    {
        return this.quietStart;
    }

    public boolean isQuietEnd()
    {
        return this.quietEnd;
    }

    public boolean isQuietRemind()
    {
        return this.quietRemind;
    }

    public String getImageUrl()
    {
        return this.imageUrl;
    }

    public String getThumbnailUrl()
    {
        return this.thumbnailUrl;
    }

    public String getGuildId()
    {
        return this.guildId;
    }

    public String getChannelId()
    {
        return this.chanId;
    }

    public ZonedDateTime getDeadline()
    {
        return this.rsvpDeadline;
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

    public ScheduleEntry setTitle(String title)
    {
        this.entryTitle = title;
        return this;
    }

    public ScheduleEntry setStart(ZonedDateTime start)
    {
        this.entryStart = start;
        return this;
    }

    public ScheduleEntry setEnd(ZonedDateTime end)
    {
        this.entryEnd = end;
        return this;
    }

    public ScheduleEntry setComments(ArrayList<String> comments)
    {
        this.entryComments = comments;
        return this;
    }

    public ScheduleEntry setRepeat(Integer repeat)
    {
        this.entryRepeat = repeat;
        return this;
    }

    public ScheduleEntry setTitleUrl(String url)
    {
        this.titleUrl = url;
        return this;
    }

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

    public ScheduleEntry setGoogleId(String id)
    {
        this.googleId = id;
        return this;
    }

    public ScheduleEntry setExpire(ZonedDateTime expire)
    {
        this.expire = expire;
        return this;
    }

    public ScheduleEntry setImageUrl(String url)
    {
        this.imageUrl = url;
        return this;
    }

    public ScheduleEntry setThumbnailUrl(String url)
    {
        this.thumbnailUrl = url;
        return this;
    }

    public ScheduleEntry setQuietStart(boolean bool)
    {
        this.quietStart = bool;
        return this;
    }

    public ScheduleEntry setQuietEnd(boolean bool)
    {
        this.quietEnd = bool;
        return this;
    }

    public ScheduleEntry setQuietRemind(boolean bool)
    {
        this.quietRemind = bool;
        return this;
    }

    public ScheduleEntry setStarted(boolean bool)
    {
        this.hasStarted = bool;
        return this;
    }

    public ScheduleEntry setId(Integer id)
    {
        this.entryId = id;
        return this;
    }

    public ScheduleEntry setMessageObject(Message msg)
    {
        this.chanId = msg.getChannel().getId();
        this.guildId = msg.getGuild().getId();
        this.msgId = msg.getId();
        return this;
    }

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

    public ScheduleEntry setRsvpLimits(Map<String, Integer> limits)
    {
        this.rsvpLimits = limits;
        return this;
    }

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

    public ScheduleEntry setRsvpDeadline(ZonedDateTime deadline)
    {
        this.rsvpDeadline = deadline;
        return this;
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

        String body = "" +
                "Title:  \"" + this.getTitle() + "\"\n" +
                "Start:  " + this.getStart().format(dtf) + "\n" +
                "End:    " + this.getEnd().format(dtf) + "\n" +
                "Repeat: " + MessageGenerator.getRepeatString(this.getRepeat(), true) + " (" + this.getRepeat() + ")" + "\n";

        // title url
        if(this.getTitleUrl()!=null)
        {
            body += "Url: \"" + this.getTitleUrl() + "\"\n";
        }

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

        // image
        if(this.getImageUrl() != null)
        {
            body += "Image: \"" + this.getImageUrl() + "\"\n";
        }

        // thumbnail
        if(this.getThumbnailUrl() != null)
        {
            body += "Thumbnail: \"" + this.getThumbnailUrl() + "\"\n";
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
                body += "// Limits\n";
                for(String key : this.getRsvpLimits().keySet())
                {
                    body += key + " - " + this.getRsvpLimits().get(key) + "\n";
                }
            }
        }

        // comments
        if(!this.getComments().isEmpty())
        {
            body += "// Comments\n";
        }
        for(int i=1; i<this.getComments().size()+1; i++)
        {
            body += "[" + i + "] \"" + this.getComments().get(i-1) + "\"\n";
        }
        return body;
    }
}
