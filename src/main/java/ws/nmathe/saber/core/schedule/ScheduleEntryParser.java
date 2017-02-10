package ws.nmathe.saber.core.schedule;


import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;
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
            if( chanSetManager.getStyle(msg.getChannel().getId()).equals("embed") )     //
            {                                                                           // if embed setting is on,
                ScheduleEntry se = EmbedParser.parse(msg.getEmbeds().get(0), msg);      // try to parse the message as an embed
                if( se != null )                                                        // if it's successful, return the entry
                    return se;                                                          // otherwise parse as a plain
            }

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
            LocalDate dateStart;
            LocalDate dateEnd;
            LocalTime timeStart;
            LocalTime timeEnd;

            String[] tmp;

            ZoneId zone = chanSetManager.getTimeZone( msg.getChannel().getId() );
            if( lines[2].contains(" to ") )
            {
                tmp = lines[2].split(" from ");
                dateStart = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                        DateTimeFormatter.ofPattern("< MMMM d >yyyy"));

                tmp = tmp[1].split(" to ");
                timeStart = LocalTime.parse(tmp[0], DateTimeFormatter.ofPattern(timeFormatter));

                if( tmp[1].contains(" at ") )
                {
                    tmp = tmp[1].split(" at ");
                    dateEnd = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                            DateTimeFormatter.ofPattern("< MMMM d >yyyy"));
                }
                else
                {
                    dateEnd = dateStart;
                }

                timeEnd = LocalTime.parse(tmp[1], DateTimeFormatter.ofPattern(timeFormatter));
            }
            else
            {
                tmp = lines[2].split(" at ");
                dateStart = LocalDate.parse(tmp[0] + ZonedDateTime.now(zone).getYear(),
                        DateTimeFormatter.ofPattern("< MMMM d >yyyy"));
                timeStart = LocalTime.parse(tmp[1], DateTimeFormatter.ofPattern(timeFormatter));

                timeEnd = timeStart;
                dateEnd = dateStart;
            }

            // the third line is the repeat info \\
            repeat = ScheduleEntryParser.getRepeatBits( lines[3].replace("> ","") );

            // the fourth line is empty space,     \\

            // lines 5 through n-2 are comments,
            // iterate every two to avoid the new line padding \\
            for (int c = 5; c < lines.length - 3; c += 2)
            {
                if( !lines[c].trim().isEmpty() )
                    eComments.add(lines[c]);
            }

            // line n-1 is an empty space \\

            // the last line contains the ID, minutes til timer, and repeat \\
            // of form: "[ID: XXXX](TIME TIL) repeats xxx\n"
            eID = Integer.decode("0x" + lines[lines.length - 2].replace("[ID: ", "").split("]")[0]);
            Integer Id = schedManager.newId( eID );

            ZonedDateTime now = ZonedDateTime.now();
            eStart = ZonedDateTime.of(dateStart, timeStart, zone);
            eEnd = ZonedDateTime.of(dateEnd, timeEnd, zone);

            if (eStart.isBefore(now) && eEnd.isBefore(now))
            {
                eStart = eStart.plusYears(1);
                eEnd = eEnd.plusYears(1);
            }
            if (eEnd.isBefore(eStart))
            {
                eEnd = eEnd.plusDays(1);
            }

            return new ScheduleEntry(Id, eTitle, eStart, eEnd, eComments, repeat,
                    msg.getId(), msg.getChannel().getId(), msg.getGuild().getId());
        }
        catch( Exception e )
        {
            __out.printOut(ScheduleEntryParser.class, "Failed to parse a schedule entry from server " +
                    msg.getGuild().getId() + " - " + msg.getGuild().getName() );
            //MessageUtilities.deleteMsg( msg, null );
            return null;
        }
    }


    public static Message generate(String eTitle, ZonedDateTime start, ZonedDateTime end, ArrayList<String> eComments,
                                  int eRepeat, Integer eId, String cId)
    {
        if( chanSetManager.getStyle(cId).equals("embed") )
        {
            MessageEmbed embed = EmbedParser.generate(eTitle, start, end, eComments, eRepeat, eId, cId);
            return new MessageBuilder().setEmbed(embed).build();
        }
        else
        {
            String msg = genContent(eTitle, start, end, eComments, eRepeat, eId, cId);
            return new MessageBuilder().append(msg).build();
        }
    }

    private static String genContent(String eTitle, ZonedDateTime start, ZonedDateTime end, ArrayList<String> eComments,
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

        String secondLine = start.format(DateTimeFormatter.ofPattern("< MMMM d >"));
        if( start.until(end, ChronoUnit.SECONDS)==0 )
        {
            secondLine += " at " + start.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else if( start.until(end, ChronoUnit.DAYS)>=1 )
        {
            secondLine += " from " + start.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + end.format(DateTimeFormatter.ofPattern("< MMMM d >")) + " at " +
                    end.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else
        {
            secondLine += " from " + start.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + end.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
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
        msg += "[ID: " + Integer.toHexString(eId) + "]" + ScheduleEntryParser.genTimer(start,end) + "\n";

        // cap the code block
        msg += "```";

        return msg;
    }

    public static int parseWeeklyRepeat(String str)
    {
        str = str.toLowerCase();
        int bits = 0;
        if( str.contains("su") )
            bits |= 1;
        if( str.contains("mo") )
            bits |= 1<<1;
        if( str.contains("tu") )
            bits |= 1<<2;
        if( str.contains("we") )
            bits |= 1<<3;
        if( str.contains("th") )
            bits |= 1<<4;
        if( str.contains("fr") )
            bits |= 1<<5;
        if( str.contains("sa") )
            bits |= 1<<6;
        return bits;
    }

    private static int getRepeatBits(String str)
    {
        if( str.equals("does not repeat") )
            return 0;
        if( str.equals("repeats daily") )
            return 0b1111111;

        int bits = 0;
        if( str.startsWith("repeats weekly on ") )
        {
            ScheduleEntryParser.parseWeeklyRepeat(str);
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

    static String genTimer(ZonedDateTime start, ZonedDateTime end)
    {
        String timer;
        long timeTilStart = ZonedDateTime.now().until(start, ChronoUnit.SECONDS);
        long timeTilEnd = ZonedDateTime.now().until(end, ChronoUnit.SECONDS);

        if(start.isAfter(ZonedDateTime.now()))
        {
            timer = "(begins ";
            if( timeTilStart < 60 * 60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilStart/(60));
                if( minutesTil <= 1)
                    timer += "in a minute.)";
                else
                    timer += "in " + minutesTil + " minutes.)";
            }
            else if( timeTilStart < 24 * 60 * 60 )
            {
                int hoursTil = (int)Math.ceil((double)timeTilStart/(60*60));
                if( hoursTil <= 1)
                    timer += "within the hour.)";
                else
                    timer += "in " + hoursTil + " hours.)";
            }
            else
            {
                int daysTil = (int) ChronoUnit.DAYS.between(ZonedDateTime.now(), start);
                if( daysTil <= 1)
                    timer += "tomorrow.)";
                else
                    timer += "in " + daysTil + " days.)";
            }
        }
        else // if the event has started
        {
            timer = "(ends ";
            if( timeTilEnd < 30*60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
                if( minutesTil <= 1)
                    timer += "in a minute.)";
                else
                    timer += "in " + minutesTil + " minutes.)";
            }

            else if( timeTilEnd < 24 * 60 * 60 )
            {
                int hoursTil = (int)Math.ceil((double)timeTilEnd/(60*60));
                if( hoursTil <= 1)
                    timer += "within one hour.)";
                else
                    timer += "in " + hoursTil + " hours.)";
            }
            else
            {
                int daysTil = (int) ChronoUnit.DAYS.between(ZonedDateTime.now(), end);
                if( daysTil <= 1)
                    timer += "tomorrow.)";
                else
                    timer += "in " + daysTil + " days.)";
            }
        }
        return timer;
    }

    /**
     * parse and generate functions for embeds
     */
    static class EmbedParser
    {
        static ScheduleEntry parse(MessageEmbed embed, Message msg)
        {
            try {
                String cId = msg.getChannel().getId();
                String str = embed.getDescription();

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
                repeat = getRepeatBits(lines[3].replace("> ", "").replace("```", ""));


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

                return new ScheduleEntry(Id, eTitle, eStart, eEnd, eComments, repeat, msg.getId(), msg.getChannel().getId(), msg.getGuild().getId());
            } catch(Exception e)
            {
                __out.printOut(ScheduleEntryParser.class, "Failed to parse a schedule entry from server " +
                        msg.getGuild().getId() + " - " + msg.getGuild().getName() );
                //MessageUtilities.deleteMsg( msg, null );
                return null;
            }
        }

        private static MessageEmbed generate(String eTitle, ZonedDateTime eStart, ZonedDateTime eEnd, ArrayList<String> eComments,
                                            int eRepeat, Integer eId, String cId)
        {
            EmbedBuilder builder = new EmbedBuilder();

            builder.setDescription(generateDesc(eTitle, eStart, eEnd, cId, eRepeat, eComments, eId));

            return builder.build();
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
            String thirdLine = "> " + getRepeatString( eRepeat ) + "\n";

            msg += firstLine + "```Markdown\n" + secondLine + thirdLine + "```\n";

            // insert each comment line with a gap line
            for( String comment : eComments )
                msg += comment + "\n\n";

            msg += "```md\n";

            msg += "[ID: " + Integer.toHexString(eId) + "]" + ScheduleEntryParser.genTimer(eStart,eEnd) + "\n";

            msg += "```";

            return msg;
        }
    }

}
