package io.schedulerbot.core;

import io.schedulerbot.Main;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * A ScheduleEntry object represents a currently scheduled entry is either waiting to start or has already started
 * start and end functions are to be triggered upon the scheduled starting time and ending time.
 * adjustTimer is used to update the displayed 'time until' timer
 */
public class ScheduleEntry
{
    public String eTitle;                    // the title/name of the event
    public LocalTime eStart;                 // the time in (24h) when the event starts
    public LocalTime eEnd;                   // the ending time in 24h form
    public ArrayList<String> eComments;      // ArrayList of strings that make up the desc
    public Integer eID;                      // 16 bit identifier
    public int eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
    public LocalDate eDate;                  // the date in which the event begins
    public Message eMsg;                     // reference to the discord message shedule entry

    public boolean startFlag;               // flagged true when the start time has been reached

    /**
     * Thread constructor
     */
    public ScheduleEntry(String eName, LocalTime eStart, LocalTime eEnd, ArrayList<String> eComments, Integer eID, Message eMsg, int eRepeat, LocalDate eDate)
    {
        this.eTitle = eName;
        this.eStart = eStart;
        this.eEnd = eEnd;
        this.eComments = eComments;
        this.eID = eID;
        this.eMsg = eMsg;
        this.eRepeat = eRepeat;
        this.eDate = eDate;

        this.startFlag = false;
    }


    public void start()
    {
        Guild guild = this.eMsg.getGuild();
        String startMsg = "@everyone The event **" + this.eTitle + "** has begun!";

        MessageUtilities.sendAnnounce( startMsg, guild, null );

        this.startFlag = true;
    }

    public void end()
    {
        Guild guild = this.eMsg.getGuild();
        String endMsg = "@everyone The event **" + this.eTitle + "** has ended.";

        // announce that the event is ending
        MessageUtilities.sendAnnounce( endMsg, guild, null );

        // return eId to the pool
        Main.removeId(this.eID, this.eMsg.getGuild().getId());

        if( this.eRepeat == 0 )
        {
            this.destroy();
        }

        // if the event entry is scheduled to repeat, must be handled with now
        if( this.eRepeat == 1 )
        {
            // generate the event entry message
            String msg = ScheduleParser.generate(
                    this.eTitle,
                    this.eStart,
                    this.eEnd,
                    this.eComments,
                    this.eRepeat,
                    this.eDate.plusDays(1),
                    this.eID
            );

            MessageUtilities.editMsg(msg, this.eMsg, (m) -> Main.handleScheduleEntry(Main.scheduleParser.parse(m),m.getGuild().getId()));
        }
        else if( this.eRepeat == 2 )
        {
            // generate the event entry message
            String msg = ScheduleParser.generate(
                    this.eTitle,
                    this.eStart,
                    this.eEnd,
                    this.eComments,
                    this.eRepeat,
                    this.eDate.plusDays(7),
                    this.eID
            );

            MessageUtilities.editMsg(msg, this.eMsg, (m) -> Main.handleScheduleEntry(Main.scheduleParser.parse(m),m.getGuild().getId()));
        }
    }

    public void destroy()
    {
        // delete the old entry
        MessageUtilities.deleteMsg( this.eMsg, null );
    }


    public void adjustTimer()
    {
        // convert the times into integers representing the time in seconds
        int timeTilStart = (((this.eDate.getYear() - LocalDate.now().getYear())*365*24*60*60)
                + (this.eDate.getDayOfYear()-LocalDate.now().getDayOfYear())*24*60*60)
                + this.eStart.toSecondOfDay() - LocalTime.now().toSecondOfDay();

        int timeTilEnd = this.eEnd.toSecondOfDay() - this.eStart.toSecondOfDay();
        if( timeTilEnd < 0 )
        { timeTilEnd += 24*60*60; }

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
               int daysTil = (int) DAYS.between(LocalDate.now(), eDate);

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
