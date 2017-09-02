package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.User;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.utils.Logging;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

    // rsvp
    private Map<String, List<String>> rsvpMembers;
    private Map<String, Integer> rsvpLimits;

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
        this.rsvpMembers = new HashMap<>();
        this.rsvpLimits = new HashMap<>();

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
        this.reminders = (List<Date>) entryDocument.get("reminders");

        // rsvp
        this.rsvpMembers = (Map<String, List<String>>) entryDocument.get("rsvp_members");
        this.rsvpLimits = (Map<String, Integer>) entryDocument.get("rsvp_limits");

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
     * If an event's notification was sent more than three minutes late, notify the discord user who administrates
     * the bot application
     * @param time (Instant) the time the message first attempted to send
     * @param type (String) the type of notification sent
     */
    private void checkDelay(Instant time, String type)
    {
        long diff =  Instant.now().getEpochSecond() - time.plusSeconds(60*3).getEpochSecond();
        if(diff > 0)
        {
            JDA jda = Main.getShardManager().isSharding() ? Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();

            User admin = jda.getUserById(Main.getBotSettingsManager().getAdminId());
            MessageUtilities.sendPrivateMsg("Event **" + this.entryTitle + "**'s [" + this.entryId + "] " +
                    type + " notification was sent **" + diff/60 + "** minutes late!", admin, null);
        }
    }


    /**
     * handles sending reminder notifications
     */
    public void remind()
    {
        Message msg = this.getMessageObject();

        if(msg == null) return;         // if msg object is bad
        if(this.quietRemind) return;    // if the event's reminders are silenced

        if(this.entryStart.isAfter(ZonedDateTime.now()))  // don't send reminders after an event has started
        {
            String remindMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getReminderFormat(this.chanId), this);
            List<TextChannel> channels = msg.getGuild().getTextChannelsByName(Main.getScheduleManager().getReminderChan(this.chanId), true);

            for( TextChannel chan : channels )
            {
                MessageUtilities.sendMsg(remindMsg, chan, message -> this.checkDelay(Instant.now(), "reminder"));
            }

            Logging.info(this.getClass(), "Sent reminder for event " + this.getTitle() + " [" + this.getId() + "]");
        }
    }

    /**
     * Handles when an event begins
     */
    public void start()
    {
        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        if(!this.quietStart)
        { // send the start announcement
            if(this.entryStart.isAfter(ZonedDateTime.now().minusMinutes(15))) // dont send start announcements if 10 minutes late
            {
                String startMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getStartAnnounceFormat(this.chanId), this);
                List<TextChannel> channels = msg.getGuild().getTextChannelsByName(Main.getScheduleManager().getStartAnnounceChan(this.chanId), true);

                for( TextChannel chan : channels )
                {
                    MessageUtilities.sendMsg(startMsg, chan, message -> this.checkDelay(this.getStart().toInstant(), "start"));
                }

                Logging.info(this.getClass(), "Started event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                        this.getStart().withZoneSameInstant(ZoneId.systemDefault())
                                .truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString());
            }
            else
            {
                Logging.warn(this.getClass(), "Late event start: "+this.entryTitle+" ["+this.entryId+"] "+this.entryStart);
            }
        }

        this.hasStarted = true;

        // if the entry's start time is the same as it's end
        // skip to end
        if(this.entryStart.isEqual(this.entryEnd))
        {
            this.repeat();
        }
        else
        {
            this.reloadDisplay();
        }
    }

    /**
     * handles when an event ends
     */
    public void end()
    {
        Message eMsg = this.getMessageObject();
        if( eMsg==null )
            return;

        if(!this.quietEnd)
        {
            if(this.entryEnd.isAfter(ZonedDateTime.now().minusMinutes(15))) // dont send end announcement if 60 minutes late
            {// send the end announcement
                String endMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getEndAnnounceFormat(this.chanId), this);
                List<TextChannel> channels = eMsg.getGuild().getTextChannelsByName(Main.getScheduleManager().getEndAnnounceChan(this.chanId), true);

                for( TextChannel chan : channels)
                {
                    MessageUtilities.sendMsg(endMsg, chan, message -> this.checkDelay(this.getEnd().toInstant(), "end"));
                }

                Logging.info(this.getClass(), "Ended event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                        this.getEnd().withZoneSameInstant(ZoneId.systemDefault())
                                .truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString());
            }
        }
        else
        {
            Logging.warn(this.getClass(), "Late event end: "+this.entryTitle+" ["+this.entryId+"] "+this.entryEnd);
        }

        this.repeat();
    }


    /**
     * Determines what needs to be done to an event when an event ends
     */
    private void repeat()
    {
        Message msg = this.getMessageObject();
        if( msg==null )
            return;

        if( this.entryRepeat != 0 ) // find next repeat date and edit the message
        {
            int days = this.daysUntilNextOccurrence();

            // fixes wrap-around at new years
            ZonedDateTime newStart = this.entryStart.plusDays(days).isAfter(this.entryStart) ?
                    this.entryStart.plusDays(days) : this.entryStart.plusDays(days).plusYears(1);
            ZonedDateTime newEnd = this.entryEnd.plusDays(days).isAfter(this.entryEnd) ?
                    this.entryEnd.plusDays(days) : this.entryEnd.plusDays(days).plusYears(1);

            // set the new start and end
            this.entryStart = newStart;
            this.entryEnd = newEnd;
            this.setStarted(false);

            Main.getEntryManager().updateEntry(this);
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
        if( msg == null )
            return;

        MessageUtilities.editMsg(MessageGenerator.generate(this), msg, null);
    }


    /**
     * determines how many days until the event is scheduled to repeat next from the current time
     * and using the schedule's repeat repeat bitset (eg, 2^0 - sun, 2^1 - mon, etc.)
     * @return days until next occurrence as an int
     */
    private int daysUntilNextOccurrence()
    {
        // if the eighth bit is flagged, the repeat is a daily interval (ie. every two days)
        if((this.entryRepeat & 0b10000000) == 0b10000000)
        {
            return (0b10000000 ^ this.entryRepeat);
        }

        // convert to current day of week to binary representation
        int dayOfWeek = entryStart.getDayOfWeek().getValue();
        int dayAsBitSet;
        if( dayOfWeek == 7 ) //sunday
            dayAsBitSet = 1;
        else                //monday - saturday
            dayAsBitSet = 1<<dayOfWeek;

        // if repeats on same weekday next week
        if( (dayAsBitSet | this.entryRepeat) == dayAsBitSet )
            return 7;

        // if the eighth bit is off, the event repeats on fixed days of the week (ie. on tuesday and wednesday)
        int daysTil = 0;
        // else repeats earlier
        for( int i = 1; i < 7; i++)
        {
            if( dayAsBitSet == 0b1000000 )      //if bitset is SATURDAY, then
                dayAsBitSet = 0b0000001;        //set bitset to SUNDAY
            else
                dayAsBitSet <<= 1;     // else, set to the next day

            if( (dayAsBitSet & this.entryRepeat) == dayAsBitSet )
            {
                daysTil = i;
                break;
            }
        }
        return daysTil; // if this is zero, eRepeat was zero
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
        return (limit > -1) && (size > limit);
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

    public String getGoogleId()
    {
        return this.googleId;
    }

    public String getScheduleID()
    {
        return this.chanId;
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

    public Map getRsvpMembers()
    {
        return this.rsvpMembers;
    }

    public Map getRsvpLimits()
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
            // Main.getEntryManager().removeEntry(this.getId());
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

    public ScheduleEntry setReminders(List<Date> reminders)
    {
        this.reminders = reminders;
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
}
