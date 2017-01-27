package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.__out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 */
public class EmbedParser
{
    private static ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();
    private static ScheduleManager schedManager = Main.getScheduleManager();

    public static ScheduleEntry parse(MessageEmbed embed, Message msg)
    {
        try {

            String cId = msg.getChannel().getId();
            String str = embed.getDescription();
            __out.printOut(EmbedParser.class, str);

            String eTitle;
            ZonedDateTime eStart;
            ZonedDateTime eEnd;
            ArrayList<String> eComments = new ArrayList<>();
            Integer eID;
            int repeat;

            String timeFormatter;
            if (chanSetManager.getClockFormat(cId).equals("24"))
                timeFormatter = "< H:mm >";
            else
                timeFormatter = "< h:mm a >";

            String[] lines = str.split("\n");

            eTitle = lines[0].replace(":calendar_spiral:", "").replace("``","");


            // the second line is the date and time \\
            LocalDate dateStart;
            LocalDate dateEnd;
            LocalTime timeStart;
            LocalTime timeEnd;

            String[] tmp;
            ZoneId zone = chanSetManager.getTimeZone(cId);
            if (lines[2].contains(" to ")) {
                tmp = lines[2].split(" from ");
                dateStart = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                        DateTimeFormatter.ofPattern("< MMM d >yyyy"));

                tmp = tmp[1].split(" to ");
                timeStart = LocalTime.parse(tmp[0], DateTimeFormatter.ofPattern(timeFormatter));

                if (tmp[1].contains(" at ")) {
                    tmp = tmp[1].split(" at ");
                    dateEnd = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                            DateTimeFormatter.ofPattern("< MMM d >yyyy"));
                } else {
                    dateEnd = dateStart;
                }

                timeEnd = LocalTime.parse(tmp[1], DateTimeFormatter.ofPattern(timeFormatter));
            } else {
                tmp = lines[2].split(" at ");
                dateStart = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                        DateTimeFormatter.ofPattern("< MMM d >yyyy"));
                timeStart = LocalTime.parse(tmp[1], DateTimeFormatter.ofPattern(timeFormatter));

                timeEnd = timeStart;
                dateEnd = dateStart;
            }


            // the third line is the repeat info \\
            repeat = ScheduleEntryParser.getRepeatBits(lines[3].replace("> ", "").replace("```", ""));


            for (int c = 5; c < lines.length - 4; c += 2) {
                if (!lines[c].trim().isEmpty())
                    eComments.add(lines[c]);
            }


            eID = Integer.decode("0x" + lines[lines.length - 2].replace("[ID: ", "").split("]")[0]);
            Integer Id = schedManager.newId(eID);


            ZonedDateTime now = ZonedDateTime.now();
            eStart = ZonedDateTime.of(dateStart, timeStart, zone);
            eEnd = ZonedDateTime.of(dateEnd, timeEnd, zone);

            if (eStart.isBefore(now) && eEnd.isBefore(now)) {
                eStart = eStart.plusYears(1);
                eEnd = eEnd.plusYears(1);
            }
            if (eEnd.isBefore(eStart)) {
                eEnd = eEnd.plusDays(1);
            }

            return new ScheduleEntry(eTitle, eStart, eEnd, eComments, Id, msg, repeat);
        } catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public static EmbedBuilder generate(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments,
                                 int eRepeat, Integer eId, String cId)
    {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setDescription(generateDesc(eTitle, eStart, eEnd, cId, eRepeat, eComments, eId));

        return builder;
    }

    private static String generateDesc(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, String cId, int eRepeat, ArrayList<String> eComments, Integer eId)
    {
        String timeFormatter;
        if( chanSetManager.getClockFormat(cId).equals("24") )
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
