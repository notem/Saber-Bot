package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.Logging;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    private String title;                    // the title/name of the event
    private ZonedDateTime start;             // the time when the event starts
    private ZonedDateTime end;               // the ending time
    private ArrayList<String> comments;      // list of comment strings
    private String description;              // description composition
    private EventRecurrence recurrence;

    //private Integer entryRepeat;
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
    private String location;
    private String colorCode;

    // announcement overrides
    // these hold temporary values
    private Set<Date> announcements;        // used by DB for queries
    private Map<String, Date> aDates;       // maps ID to Date in announcements
    // these are constant across events in a series
    private Map<String, String> aTimes;     // maps ID to rel-time string
    private Map<String, String> aTargets;   // maps ID to channel target
    private Map<String, String> aMessages;  // maps ID to announcement message


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
        this.msgId   = null;
        this.chanId  = channel.getId();
        this.guildId = channel.getGuild().getId();

        // entry parameters
        this.title       = title;
        this.start       = start;
        this.end         = end;
        this.recurrence  = new EventRecurrence(start);
        this.comments    = new ArrayList<>();
        this.description = "%g";

        // rsvp
        this.rsvpMembers  = new LinkedHashMap<>();
        this.rsvpLimits   = new LinkedHashMap<>();
        this.rsvpDeadline = null;

        // toggles
        this.quietStart  = false;
        this.quietEnd    = false;
        this.quietRemind = false;

        // urls
        this.titleUrl     = null;
        this.imageUrl     = null;
        this.thumbnailUrl = null;

        // misc
        this.hasStarted = false;
        this.location   = null;
        this.colorCode  = null;

        // announcement overrides
        this.announcements = new HashSet<>();
        this.aDates        = new HashMap<>();
        this.aTimes        = new HashMap<>();
        this.aTargets      = new HashMap<>();
        this.aMessages     = new HashMap<>();
    }


    /**
     * Constructor for a fully initialized ScheduleEntry
     * @param entryDocument (Document) taken from the events collection in the database backing the bot
     */
    @SuppressWarnings("unchecked")
    public ScheduleEntry(Document entryDocument)
    {
        // identifiers
        this.entryId  = entryDocument.getInteger("_id");
        this.msgId    = (String) entryDocument.get("messageId");
        this.chanId   = (String) entryDocument.get("channelId");
        this.guildId  = (String) entryDocument.get("guildId");
        this.googleId = (String) entryDocument.get("googleId");

        // entry zone information
        ZoneId zone = Main.getScheduleManager().getTimeZone(this.chanId);

        // main parameters
        this.title       = entryDocument.getString("title");
        this.start       = ZonedDateTime.ofInstant((entryDocument.getDate("start")).toInstant(), zone);
        this.end         = ZonedDateTime.ofInstant((entryDocument.getDate("end")).toInstant(), zone);
        this.comments    = (ArrayList<String>) entryDocument.get("comments");
        this.hasStarted  = (boolean) entryDocument.get("hasStarted");
        this.description = entryDocument.get("description")==null ?
                "%g" : entryDocument.getString("description");

        // construct the recurrence object
        ZonedDateTime dtStart = entryDocument.get("orig_start")==null ?
                this.start : ZonedDateTime.ofInstant((entryDocument.getDate("orig_start")).toInstant(), zone);
        if (entryDocument.get("recurrence") != null)
        {   // new recurrence design
            this.recurrence = new EventRecurrence(entryDocument.getInteger("recurrence"), dtStart);
        }
        if (entryDocument.get("expire") != null)
        {   // if event has an expire date
            this.recurrence.setExpire(ZonedDateTime.ofInstant(entryDocument.getDate("expire").toInstant(), zone));
        }
        else if (entryDocument.get("count") != null)
        {   // else if event has a count limit
            this.recurrence.setCount(entryDocument.getInteger("count"));
        }

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

        // toggles
        this.quietStart = (boolean) (entryDocument.get("start_disabled") != null ?
                entryDocument.get("start_disabled") : false);
        this.quietEnd = (boolean) (entryDocument.get("end_disabled") != null ?
                entryDocument.get("end_disabled") : false);
        this.quietRemind = (boolean) (entryDocument.get("reminders_disabled") != null ?
                entryDocument.get("reminders_disabled") : false);

        // urls
        this.titleUrl     = entryDocument.getString("url");
        this.imageUrl     = entryDocument.getString("image");
        this.thumbnailUrl = entryDocument.getString("thumbnail");

        // announcement overrides
        this.announcements = entryDocument.get("announcements") == null ?
                new HashSet<>() : new HashSet<>(((List<Date>) entryDocument.get("announcements")));
        this.aDates = entryDocument.get("announcement_dates") == null ?
                new HashMap<>() : (Map<String, Date>) entryDocument.get("announcement_dates");
        this.aTimes = entryDocument.get("announcement_times") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_times");
        this.aTargets = entryDocument.get("announcement_targets") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_targets");
        this.aMessages = entryDocument.get("announcement_messages") == null ?
                new HashMap<>() : (Map<String,String>) entryDocument.get("announcement_messages");

        // misc
        this.location = entryDocument.get("location") == null ?
                null : entryDocument.getString("location");
        this.colorCode = entryDocument.get("color") == null ?
                null : entryDocument.getString("color");
    }

    /**
     * handles sending special announcements
     */
    public void announce()
    {
        Message message = this.getMessageObject();
        if (message == null) return;

        // find all expired Dates' announcement IDs
        Collection<String> expired = new ArrayList<>();
        for(String ID : this.aTimes.keySet())
        {
            Date date = this.aDates.get(ID);
            if(date!=null && date.before(new Date())) expired.add(ID);
        }

        // remove all the processed announcements and update event
        this.announcements.removeIf(date->date.before(new Date())); // remove from announcement set
        expired.forEach(key-> this.aDates.remove(key));           // remove from ID mapping to announcement set

        // update db entry
        Main.getEntryManager().updateEntry(this, false);

        // send announcements
        expired.forEach(key->
        {
            String text = ParsingUtilities.processText(this.aMessages.get(key), this, true);
            String target = this.aTargets.get(key);

            // send announcement
            this.makeAnnouncement(message, text, target);
            Logging.event(this.getClass(), "Sent special announcement for event " +
                    this.getTitle() + " [" + this.getId() + "]");
        });
    }

    /**
     * handles sending reminder notifications
     */
    public void remind()
    {
        Message message = this.getMessageObject();
        if (message == null) return;

        // remove expired reminders
        this.reminders.removeIf(date -> date.before(new Date()));
        this.endReminders.removeIf(date -> date.before(new Date()));

        // attempt to update the db record
        Main.getEntryManager().updateEntry(this, false);

        // send reminder
        if (!this.quietRemind)
        {
            // parse message and get the target channels
            String text = ParsingUtilities.processText(Main.getScheduleManager().getReminderFormat(this.chanId), this, true);
            String identifier = Main.getScheduleManager().getReminderChan(this.chanId);
            if (identifier != null)
            {
                this.makeAnnouncement(message, text, identifier);
                Logging.event(this.getClass(), "Sent reminder for event " + this.getTitle() + " [" + this.getId() + "]");
            }
        }
    }

    /**
     * Handles when an event begins
     */
    public void start()
    {
        Message message = this.getMessageObject();
        if (message == null) return;

        // create start message and grab identifier before modifying entry
        String text = ParsingUtilities.processText(Main.getScheduleManager().getStartAnnounceFormat(this.chanId), this, true);
        String identifier = Main.getScheduleManager().getStartAnnounceChan(this.chanId);

        // is the announcement late?
        Integer threshold = Main.getGuildSettingsManager().getGuildSettings(this.getGuildId()).getLateThreshold();
        boolean late = this.start.isAfter(ZonedDateTime.now().minusMinutes(threshold));

        // do database updates before sending announcement
        if (this.start.isEqual(this.end))
        {   // process event repeat
            this.repeat(message);
        }
        else // update event to has started
        {    // try to update db
            this.hasStarted = true;
            Main.getEntryManager().startEvent(this);
        }

        // send start announcement
        if (!this.quietStart)
        {
            // dont send start announcements if 15 minutes late
            if (late)
            {
                if (identifier != null)
                {
                    this.makeAnnouncement(message, text, identifier);
                    Logging.event(this.getClass(), "Started event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                            this.getStart().withZoneSameInstant(ZoneId.systemDefault())
                                    .truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString());
                }
            }
            else
            {
                Logging.warn(this.getClass(), "Late event start: "+this.title +" ["+this.entryId+"] "+this.start);
            }
        }
    }

    /**
     * handles when an event ends
     */
    public void end()
    {
        Message message = this.getMessageObject();
        if (message == null) return;

        // create the announcement message before modifying event
        String text = ParsingUtilities.processText(Main.getScheduleManager()
                .getEndAnnounceFormat(this.chanId), this, true);
        String identifier = Main.getScheduleManager().getEndAnnounceChan(this.chanId);

        // check if the event is late
        Integer threshold = Main.getGuildSettingsManager().getGuildSettings(this.getGuildId()).getLateThreshold();
        Boolean late = this.end.isAfter(ZonedDateTime.now().minusMinutes(threshold));

        // update entry
        this.repeat(message);

        // send announcement
        if (!this.quietEnd)
        {
            // dont send end announcement if late
            if (late)
            {
                // send the end announcement
                if (identifier != null)
                {
                    this.makeAnnouncement(message, text, identifier);
                    String logStr = "Ended event \"" + this.getTitle() + "\" [" + this.entryId + "] scheduled for " +
                            this.getEnd().withZoneSameInstant(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES).toLocalTime();
                    Logging.event(this.getClass(), logStr);
                }
            }
            else
            {
                Logging.warn(this.getClass(), "Late event end: "+this.title +" ["+this.entryId+"] "+this.end);
            }
        }
    }


    /**
     * Determines what needs to be done to an event when an event ends
     */
    public boolean repeat()
    {
        Message msg = this.getMessageObject();
        if (msg == null) return false;
        this.repeat(msg);
        return true;
    }

    private void repeat(Message message)
    {
        if (this.recurrence.shouldRepeat(this.start)) // find next repeat date and edit the message
        {
            this.setNextOccurrence();
            this.setStarted(false);

            // if the next time an event repeats is after the event's expire, delete the event
            ZonedDateTime expire = this.recurrence.getExpire();
            if (expire != null && expire.isBefore(this.getStart()))
            {
                Main.getEntryManager().removeEntry(this.entryId);
                MessageUtilities.deleteMsg(message, null);
                return;
            }

            // reload time-dependent announcements
            this.regenerateAnnouncementOverrides();
            this.reloadReminders(Main.getScheduleManager().getReminders(this.chanId));
            this.reloadEndReminders(Main.getScheduleManager().getEndReminders(this.chanId));

            // clear rsvp members list and reload reminders
            this.rsvpMembers = new HashMap<>();

            // send changes to database
            Main.getEntryManager().updateEntry(this, true);
        }
        else // otherwise remove entry and delete the message
        {
            MessageUtilities.deleteMsg(message, null);
            Main.getEntryManager().removeEntry(this.entryId);
        }
    }


    /**
     * processes a channel identifier (either a channel name or snowflake ID) into a valid channel
     * and sends an event announcement
     */
    private void makeAnnouncement(Message message, String content, String targetIdentifier)
    {
        boolean success = false;

        // if the identifier is all digits, attempt to treat the identifier as a snowflake ID
        if(targetIdentifier.matches("\\d+"))
        {
            try
            {
                TextChannel channel = message.getGuild().getTextChannelById(targetIdentifier);
                if (channel != null)
                {
                    MessageUtilities.sendMsg(content, channel, null);
                    success = true;
                }
            }
            catch (Exception e)
            {
                Logging.warn(this.getClass(), "Error when sending announcement: "+e.getMessage());
            }
        }
        // if the announcement has not sent using the identifier as a snowflake,
        // treat the identifier as a channel name
        if (!success && !targetIdentifier.isEmpty())
        {
            List<TextChannel> channels = message.getGuild().getTextChannelsByName(targetIdentifier, true);
            for (TextChannel chan : channels)
            {
                MessageUtilities.sendMsg(content, chan, null);
            }
        }
    }


    /**
     * Edits the displayed Message to indicate the time remaining until
     * the entry is scheduled to begin/end
     */
    void reloadDisplay()
    {
        this.getMessageObject((message)->
                MessageUtilities.editMsg(MessageGenerator.generate(this), message, null));
    }


    /**
     * generates a temporary RSVP group role for dynamic user mentioning
     * the role will last for some time before being removed
     * @param group the rsvp group
     * @return the newly created Role
     */
    public Role spawnRole(String group)
    {
        List<String> members = this.rsvpMembers.get(group);
        JDA jda = Main.getShardManager().getJDA(this.guildId);
        Guild guild = jda.getGuildById(guildId);

        // create the event RSVP role
        Role role = guild.getController().createRole()
                .setName(group)
                .setMentionable(true)
                .setColor(Color.ORANGE)
                //.setColor((int) (Math.random()*Integer.MAX_VALUE+1)) // use a random color
                .complete();

        // add all RSVP'ed members to the role
        members.forEach(memberId ->
        {
            if (memberId.matches("\\d+"))
            {
                Member member = guild.getMemberById(memberId);
                guild.getController().addSingleRoleToMember(member, role)
                        .reason("dynamic RSVP role for event announcement").complete();
            }
        });

        // automatically remove the role after 5 minutes
        role.delete().queueAfter(60*5, TimeUnit.SECONDS);
        return role;
    }

    /**
     * handles processing an reaction event
     * @param event reaction event
     * @return true if the reactin should be removed
     */
    public boolean handleRSVPReaction(MessageReactionAddEvent event)
    {
        // if past the deadline, don't add handle new RSVPs
        if (this.getDeadline()!=null &&
                this.getDeadline().plusDays(1).isBefore(ZonedDateTime.now()))
            return false;

        MessageReaction.ReactionEmote emote = event.getReactionEmote();
        Map<String, String> options = Main.getScheduleManager().getRSVPOptions(this.getChannelId());
        String clearEmoji = Main.getScheduleManager().getRSVPClear(this.getChannelId());

        boolean emoteIsRSVP = false;
        String emoteKey = "";

        // does options contain the emote's name?
        String emoteName = emote.getName();
        String emoteId = emote.getId();
        if(emoteName!=null && (options.containsKey(emoteName) || emoteName.equals(clearEmoji)))
        {
            emoteIsRSVP = true;
            emoteKey = emoteName;
        }
        // does options contain the emote's ID?
        else if(emoteId!=null && (options.containsKey(emoteId) || emoteId.equals(clearEmoji)))
        {
            emoteIsRSVP = true;
            emoteKey = emoteId;
        }
        // only if options contained the emote's name or ID
        if(emoteIsRSVP)
        {
            String logging = Main.getScheduleManager().getRSVPLogging(chanId);
            if(emoteKey.equals(clearEmoji))
            {
                // remove the user from groups
                boolean atLeastOne = false;
                for(String group : options.values())
                {
                    List<String> members = this.getRsvpMembersOfType(group);
                    if (members.contains(event.getUser().getId()))
                    {
                        members.remove(event.getUser().getId());
                        this.setRsvpMembers(group, members);
                        atLeastOne = true;
                    }
                }

                if(atLeastOne)  // if the user was removed from at least one group
                {
                    // send rsvp rescinded confirmation to the user
                    if (Main.getScheduleManager().isRSVPConfirmationsEnabled(chanId))
                    {
                        String content = "You have rescinded your RSVP(s) for **" + this.getTitle() + "**";
                        MessageUtilities.sendPrivateMsg(content, event.getUser(), null);
                    }

                    // log the rsvp action
                    if (!logging.isEmpty() && logging.matches("\\d+"))
                    {
                        String content = "<@" + event.getUser().getId() + "> has rescinded their RSVP(s) for **" +
                                this.getTitle() + "** - :id: **" + ParsingUtilities.intToEncodedID(this.getId()) + "**";
                        TextChannel loggingChannel = event.getJDA().getTextChannelById(logging);
                        if (loggingChannel != null)
                            MessageUtilities.sendMsg(content, loggingChannel, null);
                    }

                    Main.getEntryManager().updateEntry(this, false);
                }
            }
            else
            {
                // get the name of the rsvp group
                String name = options.get(emoteKey);

                // if the rsvp group is full, do nothing
                if (!this.isFull(name))
                {
                    // add the user to the rsvp type
                    List<String> members = this.getRsvpMembersOfType(name);
                    if (!members.contains(event.getUser().getId()))
                    {
                        members.add(event.getUser().getId());
                        this.setRsvpMembers(name, members);

                        // remove the user from any other rsvp lists for that event if exclusivity is enabled
                        boolean hasChangedRSVP = false;
                        if (Main.getScheduleManager().isRSVPExclusive(event.getChannel().getId()))
                        {
                            for(String group : options.values())
                            {
                                if (!group.equals(name))
                                {
                                    members = this.getRsvpMembersOfType(group);
                                    if (members.contains(event.getUser().getId()))
                                    {
                                        members.remove(event.getUser().getId());
                                        hasChangedRSVP = true;
                                        this.setRsvpMembers(group, members);
                                    }
                                }
                            }
                        }

                        // send rsvp confirmation to the user
                        if (Main.getScheduleManager().isRSVPConfirmationsEnabled(chanId))
                        {
                            String content = "You " + (hasChangedRSVP ? "have changed your RSVP to":"have RSVPed") +
                                    " ``" + name + "`` for **" + this.getTitle() + "**";
                            MessageUtilities.sendPrivateMsg(content, event.getUser(), null);
                        }

                        // log the rsvp action
                        if (!logging.isEmpty() && logging.matches("\\d+"))
                        {
                            String content = "<@" + event.getUser().getId() + "> " +
                                    (hasChangedRSVP ? "has changed their RSVP to":"has RSVPed") +" ``" + name + "`` for **" +
                                    this.getTitle() + "** - :id: **" + ParsingUtilities.intToEncodedID(this.getId()) + "**";
                            TextChannel loggingChannel = event.getJDA().getTextChannelById(logging);
                            if (loggingChannel != null)
                                MessageUtilities.sendMsg(content, loggingChannel, null);
                        }

                        // update the event with the adjusted RSVP list
                        Main.getEntryManager().updateEntry(this, false);
                    }
                }
            }
        }

        // finally, indicate the reaction should be removed if it is an rsvp reaction
        return emoteIsRSVP;
    }


    /**
     * updates the schedule entry's start and end date-times to the next
     * scheduled occurrence for the event
     */
    private ScheduleEntry setNextOccurrence()
    {
        // update to next occurrence
        this.start = this.recurrence.next(this.start);
        this.end   = this.recurrence.next(this.end);

        ZonedDateTime now = ZonedDateTime.now();
        if (this.start.isBefore(now))
        {
            Logging.warn(this.getClass(),
                    "The next occurrence date for event #"+this.getId()+" is invalid! ("+this.recurrence+")");
        }
        return this;
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
        Integer size  = this.rsvpMembers.get(type)==null ? 0 : this.rsvpMembers.get(type).size();
        return (limit > -1) && (size >= limit);
    }

    public String getTitle()
    {
        return this.title;
    }

    public ZonedDateTime getStart()
    {
        return this.start;
    }

    public ZonedDateTime getEnd()
    {
        return this.end;
    }

    public ArrayList<String> getComments()
    {
        return this.comments;
    }

    public Integer getId()
    {
        return this.entryId;
    }

    public Integer getRepeat()
    {
        return this.recurrence.getRepeat();
    }

    public EventRecurrence getRecurrence()
    {
        return this.recurrence;
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

    public ZonedDateTime getExpire()
    {
        return this.recurrence.getExpire();
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

    public String getLocation()
    {
        return this.location;
    }

    public String getColor()
    {
        return this.colorCode;
    }

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
        return new ArrayList<>(members);
    }

    /**
     * retrieves full map of rsvp member
     */
    public Map<String, List<String>> getRsvpMembers()
    {
        return new HashMap<>(this.rsvpMembers);
    }

    /**
     * retrieves full map of rsvp limits
     */
    public Map<String, Integer> getRsvpLimits()
    {
        return this.rsvpLimits;
    }

    /**
     * retrieves the event's raw description (unprocessed)
     */
    public String getDescription()
    {
        return this.description;
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
     * retrieves the full map of announcement override relative times
     */
    public Map<String, String> getAnnouncementTimes()
    {
        return this.aTimes;
    }

    /**
     * retrieves the full map of announcement override target channels
     */
    public Map<String, String> getAnnouncementTargets()
    {
        return this.aTargets;
    }

    /**
     * retrieves the full map of announcement override message format strings
     */
    public Map<String, String> getAnnouncementMessages()
    {
        return this.aMessages;
    }

    /**
     * retrieves the full map of announcement override active date object
     */
    public Map<String, Date> getAnnouncementDates()
    {
        return this.aDates;
    }

    /**
     * retrieves the full list of active date announcement objects
     */
    public Set<Date> getAnnouncements()
    {
        return this.announcements;
    }

    public String getMessageId()
    {
        return this.msgId;
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
            JDA jda = Main.getShardManager().isSharding() ?
                    Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();

            msg = jda.getTextChannelById(this.chanId)
                    .getMessageById(this.msgId)
                    .complete();
        }
        catch (Exception e)
        {
            msg = null;
        }
        return msg;
    }

    /**
     * Attempts to retrieve the discord Message, if the message does not exist
     * (or the bot can for any other reason cannot retrieve it) the method returns null
     * @return (Message) if exists, otherwise null
     */
    public void getMessageObject(Consumer<Message> success)
    {
        JDA jda = Main.getShardManager().isSharding() ?
                Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();
        TextChannel channel = jda.getTextChannelById(this.chanId);
        if (channel != null)
        {
            channel.getMessageById(this.msgId)
                    .queue(success);
        }
    }

    /*
     * Setters
     */

    /**
     * sets the entry object's title
     */
    public ScheduleEntry setTitle(String title)
    {
        this.title = title;
        return this;
    }

    /**
     * sets the entry object's start datetime
     */
    public ScheduleEntry setStart(ZonedDateTime start)
    {
        if(this.start.equals(this.end))
        {
            // if the event's end is 'off', update the end to match start
            this.end = start;
        }
        this.start = start;
        return this;
    }

    /**
     * sets the entry object's end datetime
     */
    public ScheduleEntry setEnd(ZonedDateTime end)
    {
        this.end = end;
        return this;
    }

    /**
     * sets the entry object's array of comments
     */
    public ScheduleEntry setComments(ArrayList<String> comments)
    {
        this.comments = comments;
        return this;
    }

    /**
     * sets the entry object's shouldRepeat settings
     */
    public ScheduleEntry setRepeat(Integer repeat)
    {
        this.recurrence.setRepeat(repeat);
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
        this.recurrence.setExpire(expire);
        this.recurrence.setCount(null);
        return this;
    }

    /**
     * set's the entry's event occurrence count
     */
    public ScheduleEntry setCount(Integer count)
    {
        this.recurrence.setCount(count);
        this.recurrence.setExpire(null);
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

    public ScheduleEntry setDescription(String desc)
    {
        this.description = desc;
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
     * set the location string for an event, may be null
     */
    public ScheduleEntry setLocation(String location)
    {
        this.location = location;
        return this;
    }

    public ScheduleEntry setColor(String colorCode)
    {
        this.colorCode = colorCode;
        return this;
    }

    /**
     * Set the original start datetime for an event, must not be null
     */
    public ScheduleEntry setOriginalStart(ZonedDateTime original)
    {
        this.recurrence.setOriginalStart(original);
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
            if (!this.aTimes.keySet().contains(i+""))
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
            this.aDates.put(id.toString(), Date.from(datetime.toInstant()));
        }

        // create mappings
        this.aTimes.put(id.toString(), timeString);
        this.aTargets.put(id.toString(), channelId);
        this.aMessages.put(id.toString(), message);
        return this;
    }

    /**
     * removes an announcement override (if one exists)
     */
    public ScheduleEntry removeAnnouncementOverride(Integer id)
    {
        this.aDates.remove(id.toString());
        this.aMessages.remove(id.toString());
        this.aTimes.remove(id.toString());
        this.aTargets.remove(id.toString());

        // only remove from the announcements list if there are no other announcements scheduled for that time
        Date date = this.aDates.get(id.toString());
        if ((date != null) && !this.aDates.containsValue(date))
        {
            this.announcements.remove(date);
        }
        return this;
    }

    /**
     * repopulates the announcements set and aDates
     * based on values in aTimes, aMessages, and aTargets
     */
    public ScheduleEntry regenerateAnnouncementOverrides()
    {
        for(String key : this.aTimes.keySet())
        {
            // create hard date time object
            ZonedDateTime datetime = ParsingUtilities.parseTimeString(this.aTimes.get(key), this);
            if (datetime == null)
            {
                Logging.warn(this.getClass(), "Unable to generate the time");
            }
            else if (datetime.isAfter(ZonedDateTime.now()))
            {
                // add to lists
                this.announcements.add(Date.from(datetime.toInstant()));
                this.aDates.put(key, Date.from(datetime.toInstant()));
            }
        }
        return this;
    }

    /**
     * creates string display for event comments
     */
    public String commentsToString()
    {
        StringBuilder body = new StringBuilder("// Comments\n");
        for(int i=1; i<this.getComments().size()+1; i++)
        {
            body.append("[")
                    .append(i)
                    .append("] \"")
                    .append(this.getComments().get(i - 1))
                    .append("\"\n");
        }
        return body.toString();
    }

    /**
     * creates string display for event limits
     */
    public String limitsToString()
    {
        StringBuilder body = new StringBuilder("// Limits\n");
        for(String key : this.getRsvpLimits().keySet())
        {
            Integer limit = this.getRsvpLimits().get(key);
            if (limit != null && limit > 0)
            {
                body.append(key).append(" - ")
                        .append(this.getRsvpLimits().get(key))
                        .append("\n");
            }
        }
        return body.toString();
    }

    /**
     * creates string display for event announcements
     */
    public String announcementsToString()
    {
        StringBuilder body = new StringBuilder("// Event Announcements\n");
        JDA jda = Main.getShardManager().getJDA(this.guildId);
        for (String id : this.aTimes.keySet())
        {
            TextChannel channel = jda.getTextChannelById(this.aTargets.get(id));
            body.append("(")
                    .append(Integer.parseInt(id) + 1)
                    .append(") \"")
                    .append(this.aMessages.get(id))
                    .append("\"")
                    .append(" at \"")
                    .append(this.aTimes.get(id))
                    .append("\"")
                    .append(" to \"#")
                    .append(channel == null ? "unknown_channel" : channel.getName())
                    .append("\"\n");
        }
        return body.toString();
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
                "Repeat: \"" + this.recurrence.toString() + "\"\n" +
                "Description: " + (this.getDescription().equals("%g") ? "(default)" : "\""+this.getDescription()+"\"")+"\n";

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
            body += "Expire: \"" + this.getExpire().toLocalDate() + "\"\n";

        // count
        if(this.getRecurrence().getCount() != null)
            body += "Count: \"" + this.getRecurrence().getCount() + "\"\n";

        // location
        if(this.getLocation() != null)
            body += "Location: \"" + this.getLocation() + "\"\n";

        // color
        if(this.getColor() != null)
            body += "Color: \"" + this.getColor() + "\"\n";

        // title url
        if(this.getTitleUrl()!=null)
            body += "Url: \n\"" + this.getTitleUrl() + "\"\n";

        // image
        if(this.getImageUrl() != null)
            body += "Image: \n\"" + this.getImageUrl() + "\"\n";

        // thumbnail
        if(this.getThumbnailUrl() != null)
            body += "Thumbnail: \n\"" + this.getThumbnailUrl() + "\"\n";

        // rsvp limits
        if(Main.getScheduleManager().isRSVPEnabled(this.getChannelId()))
        {
            if(this.getDeadline() != null)
                body += "Deadline: " + this.getDeadline().format(dtf) + "\n";
            if(!this.getRsvpLimits().isEmpty())
                body += "``````js\n" + this.limitsToString();
        }

        // comments
        if(!this.getComments().isEmpty())
            body += "``````js\n" + this.commentsToString();

        // announcement overrides
        if(!this.aTimes.isEmpty())
            body += "``````js\n" + this.announcementsToString();

        return body + "\n```";
    }
}
