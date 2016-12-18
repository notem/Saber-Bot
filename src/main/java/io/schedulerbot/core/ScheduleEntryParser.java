package io.schedulerbot.core;


import io.schedulerbot.Main;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 *  This class is responsible for both parsing a discord message containing information that creates a ScheduleEvent,
 *  as well as is responsible for generating the message that is to be used as the parsable discord schedule entry
 */
public class ScheduleEntryParser
{
    public static ScheduleEntry parse(Message msg)
    {
        try
        {
            String raw = msg.getRawContent();

            String eTitle;
            ZonedDateTime eStart;
            ZonedDateTime eEnd;
            ArrayList<String> eComments = new ArrayList<>();
            Integer eID;
            int eRepeat = 0;

            // split into lines
            String[] lines = raw.split("\n");


            // the first line is the title \\
            eTitle = lines[1].replaceFirst("# ", "");

            // the second line is the date and time \\
            // of form: "< DATE > from < START > to < END >\n"
            LocalDate date = LocalDate.parse(lines[2].split(" from ")[0] + LocalDate.now().getYear(), DateTimeFormatter.ofPattern("< MMMM d >yyyy"));
            LocalTime timeStart = LocalTime.parse(lines[2].split(" from ")[1].split(" to ")[0], DateTimeFormatter.ofPattern("< H:mm >"));
            LocalTime timeEnd = LocalTime.parse(lines[2].split(" from ")[1].split(" to ")[1], DateTimeFormatter.ofPattern("< H:mm >"));

            ZoneId zone = Main.guildSettingsManager.getGuildTimeZone( msg.getGuild().getId() );

            eStart = ZonedDateTime.of(date, timeStart, zone);
            eEnd = ZonedDateTime.of(date, timeEnd, zone);

            if( eStart.isBefore(ZonedDateTime.now()) )
            {
                eStart = eStart.plusYears(1);
                eEnd = eEnd.plusYears(1);
            }
            if( eEnd.isBefore(eStart) )
            {
                eEnd = eEnd.plusYears(1);
            }

            // the third line is empty space,     \\

            // lines four through n-2 are comments,
            // iterate every two to catch the new line \\
            for (int c = 4; c < lines.length - 3; c += 2)
                eComments.add(lines[c]);

            // line n-1 is an empty space \\

            // the last line contains the ID, minutes til timer, and repeat \\
            // of form: "[ID: XXXX](TIME TIL) repeats xxx\n"
            eID = Integer.decode("0x" + lines[lines.length - 2].replace("[ID: ", "").split("]")[0]);
            String[] tmp = lines[lines.length - 2].split("\\)");
            String repeat = tmp.length>1 ? tmp[1] : "no repeat";
            switch( repeat )
            {
                case " repeats daily" :
                    eRepeat = 1;
                    break;
                case " repeats weekly" :
                    eRepeat = 2;
                    break;
                default:
                    eRepeat = 0;
                    break;
            }


            // create a new thread
            return new ScheduleEntry(eTitle, eStart, eEnd, eComments, eID, msg, eRepeat);
        }
        catch( Exception e )
        {
            MessageUtilities.deleteMsg( msg, null );
            return null;
        }
    }


    public static String generate(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments,
                                  int eRepeat, Integer eId)
    {
        // the 'actual' first line (and last line) define format
        String msg = "```Markdown\n";

        eId = Main.scheduleManager.newId( eId ); // generate an ID for the entry
        String firstLine = "# " + eTitle + "\n";

        // of form: "< DATE > from < START > to < END >\n"
        String secondLine = eStart.format(DateTimeFormatter.ofPattern("< MMMM d >")) + " from " +
                eStart.format(DateTimeFormatter.ofPattern("< H:mm >")) + " to " +
                eEnd.format(DateTimeFormatter.ofPattern("< H:mm >")) + "\n";

        msg += firstLine + secondLine;

        // create an empty 'gap' line if there exists comments
        msg += "\n";

        // insert each comment line with a gap line
        for( String comment : eComments )
            msg += comment + "\n\n";

        // add the final ID and time til line
        msg += "[ID: " + Integer.toHexString(eId) + "]( )";

        switch( eRepeat )
        {
            case 1:
                msg += " repeats daily\n";
                break;
            case 2:
                msg += " repeats weekly\n";
                break;
            default:
                msg += "\n";
                break;
        }

        // cap the code block
        msg += "```";

        return msg;
    }
}
