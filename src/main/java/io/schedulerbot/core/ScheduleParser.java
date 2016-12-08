package io.schedulerbot.core;


import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Message;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;

/**
 *  This class is responsible for both parsing a discord message containing information that creates a ScheduleEvent,
 *  as well as is responsible for generating the message that is to be used as the parsable discord schedule entry
 */
public class ScheduleParser
{
    public ScheduleEntry parse(Message msg)
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
        return new ScheduleEntry( eTitle, eStart, eEnd, eComments, eID, msg, eRepeat, eDate );
    }


    public static String generate(String eTitle, LocalTime eStart, LocalTime eEnd, ArrayList<String> eComments,
                                  int eRepeat, LocalDate eDate, Integer eId)
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
        msg += "[ID: " + Integer.toHexString(eId) + "](begins in)\n";
        // cap the code block
        msg += "```";

        return msg;
    }
}
