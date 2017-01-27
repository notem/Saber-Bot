package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;
import ws.nmathe.saber.Main;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 */
public class EmbedParser
{
    public ScheduleEntry parse(MessageEmbed embed)
    {
        return null;
    }

    public static EmbedBuilder generate(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments,
                                 int eRepeat, Integer eId, String cId)
    {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setDescription(helper(eTitle, eStart, eEnd, cId, eRepeat, eComments, eId));

        return builder;
    }

    private static String helper(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, String cId, int eRepeat, ArrayList<String> eComments, Integer eId)
    {
        String timeFormatter;
        if( Main.getChannelSettingsManager().getClockFormat(cId).equals("24") )
            timeFormatter = "< H:mm >";
        else
            timeFormatter = "< h:mm a >";

        // the 'actual' first line (and last line) define format
        String msg = "";

        String firstLine = ":calendar_spiral: ``" + eTitle + "``\n";

        String secondLine = eStart.format(DateTimeFormatter.ofPattern("< MMM d >"));
        if( eStart.until(eEnd, ChronoUnit.SECONDS)==0 )
        {
            secondLine += " at " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else if( eStart.until(eEnd, ChronoUnit.DAYS)>=1 )
        {
            secondLine += " from " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + eEnd.format(DateTimeFormatter.ofPattern("< MMM d >")) + " at " +
                    eEnd.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else
        {
            secondLine += " from " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + eEnd.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }

        // third line is repeat line
        String thirdLine = "> " + ScheduleEntryParser.getRepeatString( eRepeat ) + "\n";

        msg += firstLine + "```Markdown\n" + secondLine + thirdLine + "```\n";

        // insert each comment line with a gap line
        for( String comment : eComments )
            msg += comment + "\n\n";

        msg += "```md\n";

        msg += "[ID: " + Integer.toHexString(eId) + "]( )\n";

        msg += "```";

        return msg;
    }
}
