package ws.nmathe.saber.core.schedule;

import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * A ScheduleEntry object represents a currently scheduled entry is either waiting to start or has already started
 * start and end functions are to be triggered upon the scheduled starting time and ending time.
 * adjustTimer is used to update the displayed 'time until' timer
 */
public class ScheduleEntry
{
    private Integer entryId;                      // 16 bit identifier
    private String entryTitle;                    // the title/name of the event
    private ZonedDateTime entryStart;             // the time when the event starts
    private ZonedDateTime entryEnd;               // the ending time
    private ArrayList<String> entryComments;      // ArrayList of strings that make up the desc
    private Integer entryRepeat;
    private String titleUrl;
    private List<Date> reminders;

    private String msgId;
    private String chanId;
    private String guildId;

    private boolean hasStarted;

    ScheduleEntry(Document entryDocument)
    {
        ZoneId zone = ZoneId.of((String) entryDocument.get("zone"));

        this.entryId = (Integer) entryDocument.get("_id");
        this.entryTitle = (String) entryDocument.get("title");
        this.entryStart = ZonedDateTime.ofInstant(((Date) entryDocument.get("start")).toInstant(), zone);
        this.entryEnd = ZonedDateTime.ofInstant(((Date) entryDocument.get("end")).toInstant(), zone);
        this.entryComments = (ArrayList<String>) entryDocument.get("comments");
        this.entryRepeat = (Integer) entryDocument.get("repeat");
        this.titleUrl = (String) entryDocument.get("url");
        this.reminders = (List<Date>) entryDocument.get("reminders");

        this.msgId = (String) entryDocument.get("messageId");
        this.chanId = (String) entryDocument.get("channelId");
        this.guildId = (String) entryDocument.get("guildId");
        this.hasStarted = (boolean) entryDocument.get("hasStarted");
    }

    /**
     * returns now > start
     */
    public boolean hasStarted()
    {
        return this.hasStarted;
    }

    /**
     *
     */
    public void remind()
    {
        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        Guild guild = msg.getGuild();
        String startMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getAnnounceFormat(this.chanId), this);

        Collection<TextChannel> chans = guild.getTextChannelsByName(Main.getScheduleManager().getAnnounceChan(this.chanId), true);
        for( TextChannel chan : chans )
        {
            MessageUtilities.sendMsg(startMsg, chan, null);
        }
    }

    /**
     * Handles when an entries's start time expires
     */
    public void start()
    {
        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        Guild guild = msg.getGuild();
        String startMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getAnnounceFormat(this.chanId), this);

        Collection<TextChannel> chans = guild.getTextChannelsByName(Main.getScheduleManager().getAnnounceChan(this.chanId), true);
        for( TextChannel chan : chans )
        {
            MessageUtilities.sendMsg(startMsg, chan, null);
        }

        if( this.entryStart.equals(this.entryEnd) )
        {
            this.end();
        }

        this.adjustTimer();
        this.hasStarted = true;
    }

    /**
     * handles when an entry's end time expires
     */
    public void end()
    {
        Message eMsg = this.getMessageObject();
        if( eMsg==null )
            return;

        if( !this.entryStart.equals(this.entryEnd) )
        {
            Guild guild = eMsg.getGuild();
            String endMsg = ParsingUtilities.parseMsgFormat(Main.getScheduleManager().getAnnounceFormat(this.chanId), this);

            Collection<TextChannel> chans = guild.getTextChannelsByName(Main.getScheduleManager().getAnnounceChan(this.chanId), true);
            for( TextChannel chan : chans )
            {
                MessageUtilities.sendMsg(endMsg, chan, null);
            }
        }

        if( this.entryRepeat != 0 ) // find next repeat date and edit the message
        {
            int days = this.daysUntilNextOccurrence();

            // fixes wrap-around at new years
            ZonedDateTime newStart = this.entryStart.plusDays(days).isAfter(this.entryStart) ?
                            this.entryStart.plusDays(days) : this.entryStart.plusDays(days).plusYears(1);
            ZonedDateTime newEnd = this.entryEnd.plusDays(days).isAfter(this.entryEnd) ?
                    this.entryEnd.plusDays(days) : this.entryEnd.plusDays(days).plusYears(1);

            Main.getEntryManager().updateEntry(this.entryId, this.entryTitle, newStart, newEnd, this.entryComments,
                    this.entryRepeat, this.titleUrl, this.getMessageObject());
        }
        else // otherwise remove entry and delete the message
        {
            Main.getEntryManager().removeEntry(this.entryId);
            MessageUtilities.deleteMsg( eMsg, null );
        }
    }

    /**
     * Edits the displayed Message text to indicate the time remaining until
     * the entry is scheduled to begin/end
     */
    void adjustTimer()
    {
        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        MessageUtilities.editMsg(
                MessageGenerator.generate(this.entryTitle, this.entryStart, this.entryEnd, this.entryComments,
                        this.entryRepeat, this.titleUrl, this.reminders, this.entryId, this.chanId),
                msg,
                null);
    }

    private int daysUntilNextOccurrence()
    {
        int dayOfWeek = ZonedDateTime.now().getDayOfWeek().getValue();
        int dayAsBitSet;
        if( dayOfWeek == 7 ) //sunday
            dayAsBitSet = 1;
        else                //monday - saturday
            dayAsBitSet = 1<<dayOfWeek;

        int daysTil = 0;
        if( (dayAsBitSet & this.entryRepeat) == dayAsBitSet )
            daysTil = 7;

        for( int i = 1; i < 7; i++)
        {
            if( (dayAsBitSet<<i) >= 0b1000000 ) //if shifting results in too large a string
                dayAsBitSet = 0b0000001;        //set to monday string
            else
                dayAsBitSet <<= i;     // else, set to the next day

            if( (dayAsBitSet & this.entryRepeat) == dayAsBitSet )
            {
                daysTil = i;
                break;
            }
        }

        return daysTil; // if this is zero, eRepeat was zero
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

    void setMessageObject(Message msg)
    {
        this.chanId = msg.getChannel().getId();
        this.guildId = msg.getGuild().getId();
        this.msgId = msg.getId();
    }

    public Message getMessageObject()
    {
        Message msg;
        try
        {
            msg = Main.getBotJda()
                    .getGuildById(this.guildId)
                    .getTextChannelById(this.chanId)
                    .getMessageById(this.msgId)
                    .complete();
        }
        catch( Exception e )
        {
            Main.getEntryManager().removeEntry(this.getId());
            msg = null;
        }
        return msg;
    }
}
