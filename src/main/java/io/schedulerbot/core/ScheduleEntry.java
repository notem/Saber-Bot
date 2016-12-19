package io.schedulerbot.core;

import io.schedulerbot.Main;
import io.schedulerbot.utils.AnnounceFormatParser;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.__out;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * A ScheduleEntry object represents a currently scheduled entry is either waiting to start or has already started
 * start and end functions are to be triggered upon the scheduled starting time and ending time.
 * adjustTimer is used to update the displayed 'time until' timer
 */
public class ScheduleEntry
{
    public String eTitle;                    // the title/name of the event
    public ZonedDateTime eStart;             // the time when the event starts
    public ZonedDateTime eEnd;               // the ending time
    public ArrayList<String> eComments;      // ArrayList of strings that make up the desc
    public Integer eID;                      // 16 bit identifier
    public int eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
    public Message eMsg;                     // reference to the discord message shedule entry

    public boolean startFlag;               // flagged true when the start time has been reached


    public ScheduleEntry(String eName, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments, Integer eID, Message eMsg, int eRepeat )
    {
        this.eTitle = eName;
        this.eStart = eStart;
        this.eEnd = eEnd;
        this.eComments = eComments;
        this.eID = eID;
        this.eMsg = eMsg;
        this.eRepeat = eRepeat;

        this.startFlag = false;
    }


    public void start()
    {
        if( this.eStart.equals(this.eEnd) )
        {
            this.end();
            return;
        }

        GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

        Guild guild = this.eMsg.getGuild();
        String startMsg = AnnounceFormatParser.parse( guildSettingsManager.getGuildAnnounceFormat(guild.getId()), this );

        MessageUtilities.sendAnnounce( startMsg, guild, null );

        this.startFlag = true;
        this.adjustTimer();
    }

    public void end()
    {
        ScheduleManager scheduleManager = Main.scheduleManager;
        GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

        Guild guild = this.eMsg.getGuild();
        String endMsg = AnnounceFormatParser.parse( guildSettingsManager.getGuildAnnounceFormat(guild.getId()), this );

        MessageUtilities.sendAnnounce( endMsg, guild, null );

        synchronized( scheduleManager.getScheduleLock() )
        {
            scheduleManager.removeEntry(this.eID);
        }

        if( this.eRepeat == 0 )
        {
            MessageUtilities.deleteMsg( this.eMsg, null );
        }

        // if the event entry is scheduled to repeat, must be handled with now
        if( this.eRepeat == 1 )
        {
            // generate the event entry message
            String msg = ScheduleEntryParser.generate(
                    this.eTitle,
                    this.eStart.plusDays(1),
                    this.eEnd.plusDays(1),
                    this.eComments,
                    this.eRepeat,
                    this.eID
            );

            MessageUtilities.editMsg(msg, this.eMsg, Main.scheduleManager::addEntry);
        }
        else if( this.eRepeat == 2 )
        {
            // generate the event entry message
            String msg = ScheduleEntryParser.generate(
                    this.eTitle,
                    this.eStart.plusDays(7),
                    this.eEnd.plusDays(7),
                    this.eComments,
                    this.eRepeat,
                    this.eID
            );

            MessageUtilities.editMsg(msg, this.eMsg, scheduleManager::addEntry);
        }
    }

    public void adjustTimer()
    {
        // convert the times into integers representing the time in seconds
        long timeTilStart = ZonedDateTime.now(this.eStart.getZone()).until(this.eStart, SECONDS);
        long timeTilEnd = this.eStart.until(this.eEnd, SECONDS);

       String[] lines = this.eMsg.getRawContent().split("\n");

       if( !startFlag )
       {
           if( timeTilStart < 60 * 30 )
           {
               int minutesTil = (int)Math.ceil((double)timeTilStart/(60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( minutesTil <= 1)
                   newline += "in a minute.)";
               else
                   newline += "in " + minutesTil + " minutes.)";

               String msg = "";
               for(String line : lines)
               {
                   if(line.equals(lines[lines.length-2]))
                       msg += newline;
                   else
                       msg += line;
                   if(!line.equals(lines[lines.length-1]))
                       msg += "\n";
               }

               MessageUtilities.editMsg( msg, this.eMsg, null );
           }
           else if( timeTilStart < 24 * 60 * 60 )
           {
               int hoursTil = (int)Math.ceil((double)timeTilStart/(60*60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( hoursTil <= 1)
                   newline += "within the hour.)";
               else
                   newline += "in " + hoursTil + " hours.)";

               String msg = "";
               for(String line : lines)
               {
                   if(line.equals(lines[lines.length-2]))
                       msg += newline;
                   else
                       msg += line;
                   if(!line.equals(lines[lines.length-1]))
                       msg += "\n";
               }

               MessageUtilities.editMsg( msg, this.eMsg, null );
           }

           else
           {
               int daysTil = (int) DAYS.between(ZonedDateTime.now(this.eStart.getZone()), eStart);

               String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
               if( daysTil <= 1)
                   newline += "tomorrow.)";
               else
                   newline += "in " + daysTil + " days.)";

               String msg = "";
               for(String line : lines)
               {
                   if(line.equals(lines[lines.length-2]))
                       msg += newline;
                   else
                       msg += line;
                   if(!line.equals(lines[lines.length-1]))
                       msg += "\n";
               }

               MessageUtilities.editMsg( msg, this.eMsg, null );

           }
       }
       else
       {
           if( timeTilEnd < 30*60 )
           {
               int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(ends ";
               if( minutesTil <= 1)
                   newline += "in a minute.)";
               else
                   newline += "in " + minutesTil + " minutes.)";

               String msg = "";
               for(String line : lines)
               {
                   if(line.equals(lines[lines.length-2]))
                       msg += newline;
                   else
                       msg += line;
                   if(!line.equals(lines[lines.length-1]))
                       msg += "\n";
               }

               MessageUtilities.editMsg( msg, this.eMsg, null );
           }

           else
           {
               int hoursTil = (int)Math.ceil((double)timeTilEnd/(60*60));
               String newline = lines[lines.length-2].split("\\(")[0] + "(ends ";
               if( hoursTil <= 1)
                   newline += "within one hour.)";
               else
                   newline += "in " + hoursTil + " hours.)";

               String msg = "";
               for(String line : lines)
               {
                   if(line.equals(lines[lines.length-2]))
                       msg += newline;
                   else
                       msg += line;
                   if(!line.equals(lines[lines.length-1]))
                       msg += "\n";
               }

               MessageUtilities.editMsg( msg, this.eMsg, null );
           }
       }
    }
}
