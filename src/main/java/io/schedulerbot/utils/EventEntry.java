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
public class EventEntry implements Runnable
{
    public String eTitle;                   // the title/name of the event
    public LocalTime eStart;                   // the time in (24h) when the event starts
    public LocalTime eEnd;                     // the ending time in 24h form
    public ArrayList<String> eComments;     // ArrayList of strings that make up the desc
    public Integer eID;                     // 16 bit identifier
    public int eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
    public LocalDate eDate;
    public Message eMsg;

    public Thread thread;

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

        this.thread = new Thread( this,  eName );
        this.thread.start();
    }

    @Override
    public void run()
    {
        // create the announcement message strings
        Guild guild = this.eMsg.getGuild();
        String startMsg = "@everyone The event **" + this.eTitle + "** has begun!";
        String endMsg = "@everyone The event **" + this.eTitle + "** has ended.";

        // convert the times into integers representing the time in seconds
        int timeTilStart = (((this.eDate.getYear() - LocalDate.now().getYear())*365*24*60*60)
                + (this.eDate.getDayOfYear()-LocalDate.now().getDayOfYear())*24*60*60)
                + this.eStart.toSecondOfDay() - LocalTime.now().toSecondOfDay();

        int timeTilEnd = this.eEnd.toSecondOfDay() - this.eStart.toSecondOfDay();
        if( timeTilEnd < 0 )
        { timeTilEnd += 24*60*60; }

        int wait = timeTilStart;

        // run the main operation of the thread
        try
        {
            // sleep until the day of the event starts or if the event starts in less than 24 hours
            while( timeTilStart > 24*60*60 )
            {
                if( timeTilStart < 2*24*60*60 )
                    wait = timeTilStart - 24*60*60;
                else
                    wait = 24*60*60 - LocalTime.now().toSecondOfDay();

                int days = (int) DAYS.between(LocalDate.now(), eDate);
                String[] lines = this.eMsg.getRawContent().split("\n");

                String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
                if( days <= 1)
                    newline += "tomorrow.)";
                else
                    newline += "in " + days + " days.)";

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

                MessageUtilities.editMsg( msg, this.eMsg );

                System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                        + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                        "] Sleeping for " + wait + " seconds.\n");

                Thread.sleep(wait * 1000);
                timeTilStart -= wait;
            }

            // sleep until the start time
            wait = timeTilStart - (int)(Math.floor( ((double)timeTilStart)/(60*60) )*60*60);
            if(wait==0)
            { wait = 60*60; }
            while( timeTilStart > 0 )
            {
                String[] lines = this.eMsg.getRawContent().split("\n");
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

                MessageUtilities.editMsg( msg, this.eMsg );

                System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                        + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                        "] Sleeping for " + wait + " seconds.\n");
                Thread.sleep(wait * 1000);        // sleep until the event starts
                timeTilStart -= wait;                   // decrement wait1 by one hour
                wait = 60*60;                     // set wait to one hour
            }

            // announce that the event is beginning
            if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                    guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
                guild.getPublicChannel().sendMessage( startMsg ).queue();
            else
                guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                        .sendMessage( startMsg ).queue();

            // sleep until event end time
            wait = timeTilEnd - (int)(Math.floor( ((double)timeTilEnd)/(60*60) )*60*60);
            if(wait==0)
            { wait = 60*60; }
            timeTilEnd = (int) Math.ceil( ((double)timeTilEnd)/(60*60) )*60*60;
            while( timeTilEnd > 0 )
            {
                String[] lines = this.eMsg.getRawContent().split("\n");
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

                MessageUtilities.editMsg( msg, this.eMsg );

                System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                        + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                        "] Sleeping for " + wait + " seconds.\n");

                Thread.sleep(wait * 1000);        // sleep until the event starts
                wait = 60*60;                     // set wait to one hour
                timeTilEnd -= 60*60;                   // decrement wait1 by one hour
            }

            // announce that the event is ending
            MessageUtilities.sendAnnounce( endMsg, guild );

            if( this.eRepeat == 0 )
            {
                // remove entry
                Main.removeId(this.eID, this.eMsg.getGuild().getId());

                // delete the old entry
                MessageUtilities.deleteMsg( this.eMsg );
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

                MessageUtilities.editMsg( msg, this.eMsg );
                Consumer<Message> task = Main.scheduler::parse;
                try
                {
                    this.eMsg.editMessage(msg).queue(task);
                }
                catch( Exception ignored)
                { }
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

                Consumer<Message> task = Main.scheduler::parse;
                try
                {
                    this.eMsg.editMessage(msg).queue(task);
                }
                catch( Exception ignored)
                { }
            }
        }

        // if an interrupt is received, quit early
        catch(InterruptedException ignored)
        { }
    }
}
