package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import ws.nmathe.saber.Main;
import net.dv8tion.jda.core.entities.Message;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.*;
import java.util.List;

/**
 *  This class is responsible for both parsing a discord message containing information that creates a ScheduleEvent,
 *  as well as is responsible for generating the message that is to be used as the parsable discord schedule entry
 */
class MessageGenerator
{
    static Message generate(String eTitle, ZonedDateTime start, ZonedDateTime end, ArrayList<String> eComments,
                            int eRepeat, String url, List<Date> reminders, Integer eId, String cId)
    {
        String titleUrl = url != null ? url : "https://nmathe.ws/bots/saber";
        String titleImage = "https://upload.wikimedia.org/wikipedia/en/8/8d/Calendar_Icon.png";
        String footerStr = "ID: " + Integer.toHexString(eId);

        if (!reminders.isEmpty())
        {
            footerStr += " | remind in ";
            long minutes = Instant.now().until(reminders.get(0).toInstant(), ChronoUnit.MINUTES);
            if(minutes<=120)
                footerStr += " " + minutes + "m";
            else
                footerStr += " " + Math.ceil(minutes/60) + "h";
            for (int i=1; i<reminders.size()-1; i++)
            {
                minutes = Instant.now().until(reminders.get(i).toInstant(), ChronoUnit.MINUTES);
                if(minutes<=120)
                    footerStr += ", " + minutes + "m";
                else
                    footerStr += ", " + Math.ceil(minutes/60) + "h";
            }
            if (reminders.size()>1)
            {
                minutes = Instant.now().until(reminders.get(reminders.size()-1).toInstant(), ChronoUnit.MINUTES);
                footerStr += " and ";
                if(minutes<=120)
                    footerStr += minutes + "m";
                else
                    footerStr += Math.ceil(minutes/60) + "h";
            }
        }

        EmbedBuilder builder = new EmbedBuilder();
        builder.setDescription(generateDesc(start, end, cId, eRepeat, eComments))
                .setColor(Color.DARK_GRAY)
                .setAuthor(eTitle, titleUrl, titleImage)
                .setFooter(footerStr, null);

        return new MessageBuilder().setEmbed(builder.build()).build();
    }

    private static String generateDesc(ZonedDateTime eStart, ZonedDateTime eEnd, String cId,
                                       int eRepeat, ArrayList<String> eComments)
    {
        String timeFormatter;
        if(Main.getScheduleManager().getClockFormat(cId).equals("24"))
            timeFormatter = "< H:mm >";
        else
            timeFormatter = "< h:mm a >";

        // the 'actual' first line (and last line) define format
        String msg = "";

        String timeLine = eStart.format(DateTimeFormatter.ofPattern("< MMM d >"));
        if( eStart.until(eEnd, ChronoUnit.SECONDS)==0 )
        {
            timeLine += " at " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else if( eStart.until(eEnd, ChronoUnit.DAYS)>=1 )
        {
            timeLine += " from " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + eEnd.format(DateTimeFormatter.ofPattern("< MMM d >")) + " at " +
                    eEnd.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }
        else
        {
            timeLine += " from " + eStart.format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " to " + eEnd.format(DateTimeFormatter.ofPattern(timeFormatter)) + "\n";
        }

        String repeatLine = "> " + getRepeatString( eRepeat ) + "\n";
        String zoneLine = "[" + eStart.getZone().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "]" + MessageGenerator.genTimer(eStart,eEnd) + "\n";

        msg += "```Markdown\n\n" + timeLine + repeatLine + "```\n";

        // insert each comment line with a gap line
        for( String comment : eComments )
            msg += comment + "\n\n";

        msg += "```Markdown\n\n" + zoneLine + "```";

        return msg;
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
}
