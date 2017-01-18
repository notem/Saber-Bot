package ws.nmathe.saber.core.schedule;


import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.utils.__out;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 *  This class is responsible for both parsing a discord message containing information that creates a ScheduleEvent,
 *  as well as is responsible for generating the message that is to be used as the parsable discord schedule entry
 */
public class ScheduleEntryParser
{
    private static ScheduleManager schedManager = Main.getScheduleManager();
    private static ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();

    static ScheduleEntry parse(Message msg)
    {
        try
        {
            String raw = msg.getRawContent();

            String eTitle;
            ZonedDateTime eStart;
            ZonedDateTime eEnd;
            ArrayList<String> eComments = new ArrayList<>();
            Integer eID;
            int repeat;


            String timeFormatter;
            if( chanSetManager.getClockFormat(msg.getChannel().getId()).equals("24") )
                timeFormatter = "< H:mm >";
            else
                timeFormatter = "< h:mm a >";

            // split into lines
            String[] lines = raw.split("\n");


            // the first line is the title (index 0 can be discarded) \\
            eTitle = lines[1].replaceFirst("# ", "");

            // the second line is the date and time \\
            LocalDate date;
            LocalTime timeStart;
            LocalTime timeEnd;

            ZoneId zone = chanSetManager.getTimeZone( msg.getChannel().getId() );
            try
            {
                date = LocalDate.parse(lines[2].split(" from ")[0] + ZonedDateTime.now(zone).getYear(), DateTimeFormatter.ofPattern("< MMMM d >yyyy"));
                timeStart = LocalTime.parse(lines[2].split(" from ")[1].split(" to ")[0], DateTimeFormatter.ofPattern(timeFormatter));
                timeEnd = LocalTime.parse(lines[2].split(" from ")[1].split(" to ")[1], DateTimeFormatter.ofPattern(timeFormatter));
            } catch (Exception ignored)
            {
                date = LocalDate.parse(lines[2].split(" at ")[0] + ZonedDateTime.now(zone).getYear(), DateTimeFormatter.ofPattern("< MMMM d >yyyy"));
                LocalTime time = LocalTime.parse(lines[2].split(" at ")[1], DateTimeFormatter.ofPattern(timeFormatter));
                timeStart = time;
                timeEnd = time;
            }

            // the third line is the repeat info \\
            int i = 0;                          //
            if( lines[3].startsWith(">") )      // compat fix
                i = 1;                          //

            repeat = ScheduleEntryParser.getRepeatBits( lines[3].replace("> ","") );

            // the fourth line is empty space,     \\

            // lines 5 through n-2 are comments,
            // iterate every two to avoid the new line padding \\
            for (int c = 4+i; c < lines.length - 3; c += 2)
                eComments.add(lines[c]);

            // line n-1 is an empty space \\

            // the last line contains the ID, minutes til timer, and repeat \\
            // of form: "[ID: XXXX](TIME TIL) repeats xxx\n"
            eID = Integer.decode("0x" + lines[lines.length - 2].replace("[ID: ", "").split("]")[0]);
            Integer Id = schedManager.newId( eID );

            ZonedDateTime now = ZonedDateTime.now();
            eStart = ZonedDateTime.of(date, timeStart, zone);
            eEnd = ZonedDateTime.of(date, timeEnd, zone);

            if (eStart.isBefore(now) && eEnd.isBefore(now))
            {
                eStart = eStart.plusYears(1);
                eEnd = eEnd.plusYears(1);
            }
            if (eEnd.isBefore(eStart))
            {
                eEnd = eEnd.plusDays(1);
            }

            // create a new thread
            return new ScheduleEntry(eTitle, eStart, eEnd, eComments, Id, msg, repeat);
        }
        catch( Exception e )
        {
            MessageUtilities.deleteMsg( msg, null );
            return null;
        }
    }


    public static String generate(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments,
                                  int eRepeat, Integer eId, String cId)
    {
        String timeFormatter;
        if( chanSetManager.getClockFormat(cId).equals("24") )
            timeFormatter = "< H:mm >";
        else
            timeFormatter = "< h:mm a >";

        // the 'actual' first line (and last line) define format
        String msg = "```Markdown\n";

        String firstLine = "# " + eTitle + "\n";

        // of form: "< DATE > from < START > to < END >\n"
        String secondLine = eStart.format(DateTimeFormatter.ofPattern("< MMMM d >"));
        if( eStart.until(eEnd, ChronoUnit.SECONDS)==0 )
        {
            secondLine += " at " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else
        {
            secondLine += " from " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + eEnd.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }

        // third line is repeat line
        String thirdLine = "> " + ScheduleEntryParser.getRepeatString( eRepeat ) + "\n";

        msg += firstLine + secondLine + thirdLine;

        // create an empty 'gap' line if there exists comments
        msg += "\n";

        // insert each comment line with a gap line
        for( String comment : eComments )
            msg += comment + "\n\n";

        // add the final ID and time til line
        msg += "[ID: " + Integer.toHexString(eId) + "]( )\n";

        // cap the code block
        msg += "```";

        return msg;
    }

    private static int getRepeatBits( String str )
    {
        if( str.equals("does not repeat") )
            return 0;
        if( str.equals("repeats daily") )
            return 1;

        int bits = 0;
        if( str.startsWith("repeats weekly on ") )
        {
            if( str.contains("Su") )
                bits |= 1;
            if( str.contains("Mo") )
                bits |= 1<<1;
            if( str.contains("Tu") )
                bits |= 1<<2;
            if( str.contains("We") )
                bits |= 1<<3;
            if( str.contains("Th") )
                bits |= 1<<4;
            if( str.contains("Fr") )
                bits |= 1<<5;
            if( str.contains("Sa") )
                bits |= 1<<6;
        }
        return bits;
    }

    private static String getRepeatString(int bitset)
    {
        if( bitset == 0 )
            return "does not repeat";
        if( bitset == 0b1111111 )
            return "repeats daily";

        String str = "repeats weekly on ";
        if( (bitset & 1) == 1 )
        {
            str += "Su";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "Mo";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "Tu";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "We";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "Th";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "Fr";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            str += "Sa";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        return str;
    }
}
