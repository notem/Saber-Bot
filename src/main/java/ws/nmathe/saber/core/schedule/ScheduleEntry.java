package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.utils.__out;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;

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
    private Integer entryRepeat;                  // 1 is daily, 2 is weekly, 0 is not at all

    private String msgId;
    private String chanId;
    private String guildId;


    public ScheduleEntry(Integer eId, String eName, ZonedDateTime entryStart, ZonedDateTime entryEnd,
                         ArrayList<String> eComments, int eRepeat, String msgId, String chanId, String guildId )
    {
        this.entryId = eId;
        this.entryTitle = eName;
        this.entryStart = entryStart;
        this.entryEnd = entryEnd;
        this.entryComments = eComments;
        this.entryRepeat = eRepeat;

        this.msgId = msgId;
        this.chanId = chanId;
        this.guildId = guildId;
    }

    /**
     * returns now > start
     */
    public boolean hasStarted()
    {
        return ZonedDateTime.now().isAfter(entryStart);
    }

    /**
     * Handles when an entries's start time expires
     */
    public void start()
    {
        ScheduleManager schedManager = Main.getScheduleManager();
        ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();

        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        if( this.entryStart.equals(this.entryEnd) )
        {
            this.end();
            return;
        }

        Guild guild = msg.getGuild();
        String startMsg = ParsingUtilities.parseMsgFormat( chanSetManager.getAnnounceFormat(this.chanId), this );

        Collection<TextChannel> chans = guild.getTextChannelsByName(chanSetManager.getAnnounceChan(this.chanId), true);
        for( TextChannel chan : chans )
        {
            MessageUtilities.sendMsg(startMsg, chan, null);
        }

        this.adjustTimer();
    }

    /**
     * handles when an entry's end time expires
     */
    public void end()
    {
        ScheduleManager schedManager = Main.getScheduleManager();
        ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();

        Message eMsg = this.getMessageObject();
        if( eMsg==null )
            return;

        Guild guild = eMsg.getGuild();
        String endMsg = ParsingUtilities.parseMsgFormat( chanSetManager.getAnnounceFormat(this.chanId), this );

        Collection<TextChannel> chans = guild.getTextChannelsByName(chanSetManager.getAnnounceChan(this.chanId), true);
        for( TextChannel chan : chans )
        {
            MessageUtilities.sendMsg(endMsg, chan, null);
        }

        synchronized( schedManager.getScheduleLock() )
        {
            schedManager.removeEntry(this.entryId);
        }

        if( this.entryRepeat != 0 ) // find next repeat date and edit the message
        {
            int days = this.daysUntilNextOccurrence();

            // fixes wrap-around at new years
            ZonedDateTime newStart = this.entryStart.plusDays(days).isAfter(this.entryStart) ?
                            this.entryStart.plusDays(days) : this.entryStart.plusDays(days).plusYears(1);
            ZonedDateTime newEnd = this.entryEnd.plusDays(days).isAfter(this.entryEnd) ?
                    this.entryEnd.plusDays(days) : this.entryEnd.plusDays(days).plusYears(1);

            Message msgContent = ScheduleEntryParser.generate(
                    this.entryTitle,
                    newStart,
                    newEnd,
                    this.entryComments,
                    this.entryRepeat,
                    this.entryId,
                    this.chanId
            );

            MessageUtilities.editMsg(msgContent, eMsg, schedManager::addEntry);
        }
        else // otherwise delete the message
        {
            MessageUtilities.deleteMsg( eMsg, null );
        }
    }

    /**
     * Edits the displayed Message text to indicate the time remaining until
     * the entry is scheduled to begin/end
     */
    public void adjustTimer()
    {
        ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();

        Message msg = this.getMessageObject();
        if( msg == null )
            return;

        String raw;
        if(chanSetManager.getStyle(msg.getChannel().getId()).equals("embed"))
            raw = msg.getEmbeds().get(0).getDescription();
        else
            raw = msg.getRawContent();

        String[] lines = raw.split("\n");
        String newline = lines[lines.length-2].split("\\(")[0] +
                ScheduleEntryParser.genTimer(this.entryStart,this.entryEnd);

        if(chanSetManager.getStyle(this.chanId).equals("plain"))
            MessageUtilities.editMsg( adjustTimerHelper(lines,newline), msg, null );
        else
            MessageUtilities.editEmbedMsg(
                    new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                    msg,
                    null);
    }

    /// reconstructs the full message, substituting in the new timer
    private String adjustTimerHelper( String[] lines, String newline )
    {
        String msg = "";
        for(int i = 0; i < lines.length ; i++)
        {
            if(i == lines.length-2)
                msg += newline + "\n";
            else if( i == lines.length-1 )
                msg += lines[i];
            else
                msg += lines[i] + "\n";
        }
        return msg;
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

    public void setZone(ZoneId zone)
    {
        this.entryStart.withZoneSameLocal(zone);
        this.entryEnd.withZoneSameLocal(zone);
    }

    public void setMessageObject(Message msg)
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
                            .block();
        }
        catch( Exception e )
        {
            //__out.printOut(this.getClass(), e.toString());
            msg = null;
        }
        return msg;
    }
}
