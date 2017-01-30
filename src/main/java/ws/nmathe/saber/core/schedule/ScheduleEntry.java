package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

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
    private String eTitle;                    // the title/name of the event
    private ZonedDateTime eStart;             // the time when the event starts
    private ZonedDateTime eEnd;               // the ending time
    private ArrayList<String> eComments;      // ArrayList of strings that make up the desc
    private Integer eID;                      // 16 bit identifier
    private Integer eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
    private Message eMsg;                     // reference to the discord message shedule entry

    private ScheduleManager schedManager = Main.getScheduleManager();
    private ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();

    public ScheduleEntry(String eName, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments, Integer eID, Message eMsg, int eRepeat )
    {
        this.eTitle = eName;
        this.eStart = eStart;
        this.eEnd = eEnd;
        this.eComments = eComments;
        this.eID = eID;
        this.eMsg = eMsg;
        this.eRepeat = eRepeat;
    }

    /**
     * returns now > start
     */
    public boolean hasStarted()
    {
        return ZonedDateTime.now().isAfter(eStart);
    }

    /**
     * Handles when an entries's start time expires
     */
    public void start()
    {
        if( this.eStart.equals(this.eEnd) )
        {
            this.end();
            return;
        }

        Guild guild = this.eMsg.getGuild();
        String startMsg = ParsingUtilities.parseMsgFormat( chanSetManager.getAnnounceFormat(this.eMsg.getChannel().getId()), this );

        Collection<TextChannel> chans = guild.getTextChannelsByName(chanSetManager.getAnnounceChan(this.eMsg.getChannel().getId()), true);
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
        Guild guild = this.eMsg.getGuild();
        String endMsg = ParsingUtilities.parseMsgFormat( chanSetManager.getAnnounceFormat(this.eMsg.getChannel().getId()), this );

        Collection<TextChannel> chans = guild.getTextChannelsByName(chanSetManager.getAnnounceChan(this.eMsg.getChannel().getId()), true);
        for( TextChannel chan : chans )
        {
            MessageUtilities.sendMsg(endMsg, chan, null);
        }

        synchronized( schedManager.getScheduleLock() )
        {
            schedManager.removeEntry(this.eID);
        }

        if( this.eRepeat != 0 ) // find next repeat date and edit the message
        {
            int days = this.daysUntilNextOccurrence();

            // fixes wrap-around at new years
            ZonedDateTime newStart = this.eStart.plusDays(days).isAfter(this.eStart) ?
                            this.eStart.plusDays(days) : this.eStart.plusDays(days).plusYears(1);
            ZonedDateTime newEnd = this.eEnd.plusDays(days).isAfter(this.eEnd) ?
                    this.eEnd.plusDays(days) : this.eEnd.plusDays(days).plusYears(1);

            Message msg = ScheduleEntryParser.generate(
                    this.eTitle,
                    newStart,
                    newEnd,
                    this.eComments,
                    this.eRepeat,
                    this.eID,
                    this.eMsg.getChannel().getId()
            );

            MessageUtilities.editMsg(msg, this.eMsg, schedManager::addEntry);
        }
        else // otherwise delete the message
        {
            MessageUtilities.deleteMsg( this.eMsg, null );
        }
    }

    /**
     * Edits the displayed Message text to indicate the time remaining until
     * the entry is scheduled to begin/end
     */
    public void adjustTimer()
    {
        String raw;
        if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("embed"))
            raw = this.eMsg.getEmbeds().get(0).getDescription();
        else
            raw = this.eMsg.getRawContent();

       // convert the times into integers representing the time in seconds
       long timeTilStart = ZonedDateTime.now().until(this.eStart, SECONDS);
       long timeTilEnd = ZonedDateTime.now().until(this.eEnd, SECONDS);

       String[] lines = raw.split("\n");

       if( !this.hasStarted() )
       {
           if( timeTilStart < 60 * 60 )
           {
               int minutesTil = (int)Math.ceil((double)timeTilStart/(60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( minutesTil <= 1)
                   newline += "in a minute.)";
               else
                   newline += "in " + minutesTil + " minutes.)";

               if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("plain"))
                    MessageUtilities.editMsg( adjustTimerHelper(lines,newline), this.eMsg, null );
               else
                   MessageUtilities.editEmbedMsg(
                           new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                           this.eMsg, null );
           }
           else if( timeTilStart < 24 * 60 * 60 )
           {
               int hoursTil = (int)Math.ceil((double)timeTilStart/(60*60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( hoursTil <= 1)
                   newline += "within the hour.)";
               else
                   newline += "in " + hoursTil + " hours.)";

               if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("plain"))
                   MessageUtilities.editMsg( adjustTimerHelper(lines,newline), this.eMsg, null );
               else
                   MessageUtilities.editEmbedMsg(
                           new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                           this.eMsg, null );
           }
           else
           {
               int daysTil = (int) DAYS.between(ZonedDateTime.now(this.eStart.getZone()), eStart);

               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( daysTil <= 1)
                   newline += "tomorrow.)";
               else
                   newline += "in " + daysTil + " days.)";

               if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("plain"))
                   MessageUtilities.editMsg( adjustTimerHelper(lines,newline), this.eMsg, null );
               else
                   MessageUtilities.editEmbedMsg(
                           new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                           this.eMsg, null );
           }
       }
       else // if the event has started
       {
           if( timeTilEnd < 30*60 )
           {
               int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(ends ";
               if( minutesTil <= 1)
                   newline += "in a minute.)";
               else
                   newline += "in " + minutesTil + " minutes.)";

               if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("plain"))
                   MessageUtilities.editMsg( adjustTimerHelper(lines,newline), this.eMsg, null );
               else
                   MessageUtilities.editEmbedMsg(
                           new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                           this.eMsg, null );
           }

           else
           {
               int hoursTil = (int)Math.ceil((double)timeTilEnd/(60*60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(ends ";
               if( hoursTil <= 1)
                   newline += "within one hour.)";
               else
                   newline += "in " + hoursTil + " hours.)";

               if(chanSetManager.getStyle(eMsg.getChannel().getId()).equals("plain"))
                   MessageUtilities.editMsg( adjustTimerHelper(lines,newline), this.eMsg, null );
               else
                   MessageUtilities.editEmbedMsg(
                           new EmbedBuilder().setDescription(adjustTimerHelper(lines,newline)).build(),
                           this.eMsg, null );
           }
       }
    }

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
        if( (dayAsBitSet & this.eRepeat) == dayAsBitSet )
            daysTil = 7;

        for( int i = 1; i < 7; i++)
        {
            if( (dayAsBitSet<<i) >= 0b1000000 ) //if shifting results in too large a string
                dayAsBitSet = 0b0000001;        //set to monday string
            else
                dayAsBitSet <<= i;     // else, set to the next day

            if( (dayAsBitSet & this.eRepeat) == dayAsBitSet )
            {
                daysTil = i;
                break;
            }
        }

        return daysTil; // if this is zero, eRepeat was zero
    }

    public String getTitle()
    {
        return this.eTitle;
    }

    public ZonedDateTime getStart()
    {
        return this.eStart;
    }

    public ZonedDateTime getEnd()
    {
        return this.eEnd;
    }

    public ArrayList<String> getComments()
    {
        return this.eComments;
    }

    public Integer getId()
    {
        return this.eID;
    }

    public Integer getRepeat()
    {
        return this.eRepeat;
    }

    public Message getMessage()
    {
        return this.eMsg;
    }

    public void setMessage(Message message)
    {
        this.eMsg = message;
    }

    public void setZone(ZoneId zone)
    {
        this.eStart.withZoneSameLocal(zone);
        this.eEnd.withZoneSameLocal(zone);
    }
}
