package io.schedulerbot.utils;


import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Message;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;

/**
 * file: Scheduler.java
 *
 * Scheduler parses a MessageReceivedEvent sent by the bot in response to 'create' commands into
 * a EventEntry worker thread.  A EventEntry thread lives for the length of two sleep calls.
 * The first timer measures the time until the event starts (causes an announce when reached).
 * The second timer measures the time from the event start until the event ends (causes another announce).
 * The bot clears the event from the entriesGlobal at the end of the thread's life.
 */
public class Scheduler implements Runnable
{
    public Thread thread;

    /**
     * Parses a the content of a EventEntry MessageReceivedEvent into a EventEntry worker thread.
     * parse()'s analogous partner method is generate() which generates the content that parse
     * will parse.  When parse() is modified, generate() must be modified in like to remain consistent,
     * and vice-versa.
     *
     * @return EventEntry worker thread
     */
    public EventEntry parse(Message msg)
    {
        String raw = msg.getRawContent();

        String eTitle;
        LocalTime eStart;
        LocalTime eEnd;
        ArrayList<String> eComments = new ArrayList<>();
        Integer eID;
        int eRepeat = 0;
        LocalDate eDate = LocalDate.now();

        // split into lines
        String[] lines = raw.split("\n");


        // the first line is the title \\
        eTitle = lines[1].replaceFirst("# ", "");


        // the second line is the date and time \\
        String[] secondLine = lines[2].replace("< ","").split(" >");
        String[] date_time_repeat = secondLine[0].split(", ");

        String date = date_time_repeat[0];
        if( date.toLowerCase().equals("today") )
            eDate = LocalDate.now();
        else
        {
            eDate = eDate.withMonth(Month.valueOf(date.split(" ")[0]).getValue());
            eDate = eDate.withDayOfMonth(Integer.parseInt(date.split(" ")[1]));
        }

        String[] start_end_timezone = date_time_repeat[1].split(" - ");

        if( date_time_repeat.length == 3 )
        {
            String repeat = date_time_repeat[2].replaceFirst("repeats ", "");
            if( repeat.equals("daily") )
                eRepeat = 1;
            else if( repeat.equals("weekly") )
                eRepeat = 2;
        }

        eStart = LocalTime.parse( start_end_timezone[0] );
        eEnd = LocalTime.parse( start_end_timezone[1].split(" ")[0] );

        String timezone = start_end_timezone[1].split(" ")[1];


        // the third line is empty space,     \\

        // lines four through n-2 are comments,
        // iterate every two to catch the new line \\
        for(int c = 4; c < lines.length-3; c+=2 )
            eComments.add(lines[c]);

        // line n-1 is an empty space \\

        // the last line contains the ID and minutes til timer \\
        eID = Integer.decode(
                "0x" + lines[lines.length-2].replace("[ID: ","").split("]")[0]
        ); // can throw away the minutes til timer



        // a bit of post processing
        int dateCompare = eDate.compareTo(LocalDate.now());
        int timeCompare = eStart.compareTo(LocalTime.now());
        if( dateCompare < 0 ) // if schedule's date is less then now
        {
            eDate = eDate.plusYears( 1 );
         }
        if( dateCompare == 0 && timeCompare < 0 )    // if the schedule's time is less then now
        {
            eDate = eDate.plusYears( 1 );
        }

        // create a new thread
        return new EventEntry( eTitle, eStart, eEnd, eComments, eID, msg, eRepeat, eDate );
    }


    /**
     * generates an event entry message
     *
     * @param eTitle the event entry title
     * @param eStart the event entry start time
     * @param eEnd the event entry end time
     * @param eComments an array of comment strings for the entry
     * @param eRepeat integer determining if repeat: 0-no 1-daily 2-weekly
     * @param eDate the date in which the event should start
     * @param eId the Id number to assign to the event entry
     * @return a String representing the raw content of a EventEntry Message
     */
    public static String generate(
            String eTitle,
            LocalTime eStart,
            LocalTime eEnd,
            ArrayList<String> eComments,
            int eRepeat,
            LocalDate eDate,
            Integer eId
    )
    {
        // the 'actual' first line (and last line) define format
        String msg = "```Markdown\n";

        eId = Main.newId( eId ); // generate an ID for the entry
        String firstLine = "# " + eTitle + "\n";

        String secondLine = "< " + eDate.getMonth() + " " + eDate.getDayOfMonth() + ", "
                + eStart + " - " + eEnd + " EST";
        if( eRepeat == 1 )
            secondLine += ", repeats daily >\n";
        else if( eRepeat == 2 )
            secondLine += ", repeats weekly >\n";
        else
            secondLine += " >\n";

        msg += firstLine + secondLine;

        // create an empty 'gap' line if there exists comments
        msg += "\n";

        // insert each comment line with a gap line
        for( String comment : eComments )
            msg += comment + "\n\n";

        // add the final ID and time til line
        msg += "[ID: " + Integer.toHexString(eId) + "](begins in " + "xx" + " hours.)\n";
        // cap the code block
        msg += "```";

        return msg;
    }


    public Scheduler()
    {
        this.thread = new Thread(this);
        this.thread.start();
    }


    public void run()
    {
        try
        {
            while( true )
            {
                synchronized( Main.lock )
                {
                    Main.entriesGlobal.forEach(this::handleEntry);
                }
                System.out.printf("Scheduler sleeping for a minute.\n");
                Thread.sleep( 60 * 1000 );
            }
        }
        catch(InterruptedException ignored)
        { }
    }


    private void handleEntry( Integer eId, EventEntry entry )
    {
        LocalDate now = LocalDate.now();
        LocalTime moment = LocalTime.now();
        if( !entry.startFlag )
        {
            if( now.compareTo(entry.eDate) > 0)
            {
                // something odd happened, destroy entry
                entry.destroy();
            }
            else if( now.compareTo(entry.eDate) == 0 &&
                    ( moment.compareTo(entry.eStart) >= 0 ) )
            {
                // start event
                entry.start();
                entry.adjustTimer();
            }
            else
            {
                // adjust the 'time until' displayed timer
                entry.adjustTimer();
            }
        }
        else
        {
            if( moment.compareTo(entry.eEnd) >= 0 )
            {
                // end event
                entry.end();
            }
            else
            {
                // adjust the 'time until' displayed timer
                entry.adjustTimer();
            }
        }
    }
}
