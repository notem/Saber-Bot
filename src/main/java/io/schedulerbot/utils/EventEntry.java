package io.schedulerbot.utils;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.function.Consumer;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * the EventEntry worker thread, gets created by the Scheduler
 */
public class EventEntry
{
    public String eTitle;                   // the title/name of the event
    public LocalTime eStart;                   // the time in (24h) when the event starts
    public LocalTime eEnd;                     // the ending time in 24h form
    public ArrayList<String> eComments;     // ArrayList of strings that make up the desc
    public Integer eID;                     // 16 bit identifier
    public int eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
    public LocalDate eDate;
    public Message eMsg;

    public boolean startFlag;

    /**
     * Thread constructor
     * @param eName name of the event (String)
     * @param eStart time of day the event starts (Integer)
     * @param eEnd time of day the event ends (Integer)
     * @param eComments the descriptive text of the event (String)
     * @param eID the ID of the event (Integer)
     */
    public EventEntry(String eName, LocalTime eStart, LocalTime eEnd, ArrayList<String> eComments, Integer eID, Message eMsg, int eRepeat, LocalDate eDate)
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

        if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
            guild.getPublicChannel().sendMessage( startMsg ).queue();
        else
            guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                    .sendMessage( startMsg ).queue();

        this.startFlag = true;
    }

    public void end()
    {
        Guild guild = this.eMsg.getGuild();
        String endMsg = "@everyone The event **" + this.eTitle + "** has ended.";

        // announce that the event is ending
        MessageUtilities.sendAnnounce( endMsg, guild, null );

        if( this.eRepeat == 0 )
        {
            this.destroy();
        }

        // if the event entry is scheduled to repeat, must be handled with now
        if( this.eRepeat == 1 )
        {
            // generate the event entry message
            String msg = Scheduler.generate(
                    this.eTitle,
                    this.eStart,
                    this.eEnd,
                    this.eComments,
                    this.eRepeat,
                    this.eDate.plusDays(1),
                    this.eID
            );

            Consumer<Message> parse = Main.scheduler::parse;
            MessageUtilities.editMsg( msg, this.eMsg, parse );
        }
        else if( this.eRepeat == 2 )
        {
            // generate the event entry message
            String msg = Scheduler.generate(
                    this.eTitle,
                    this.eStart,
                    this.eEnd,
                    this.eComments,
                    this.eRepeat,
                    this.eDate.plusDays(7),
                    this.eID
            );

            Consumer<Message> parse = Main.scheduler::parse;
            MessageUtilities.editMsg(msg, this.eMsg, parse);
        }
    }

    public void destroy()
    {
        Runnable destroy = () ->
        {
            synchronized( Main.lock )
            {
                // remove entry
                Main.removeId(this.eID, this.eMsg.getGuild().getId());
            }

            // delete the old entry
            MessageUtilities.deleteMsg( this.eMsg, null );
        };

        Thread t = new Thread(destroy);
        t.start();
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
