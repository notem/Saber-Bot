package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Role;
import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.Main;
import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.*;
import java.util.List;

/**
 * This class is responsible for generating the event's message for display in the Discord client
 */
public class MessageGenerator
{
    /**
     * Primary method which generates a complete Discord message object for the event
     * @param se (ScheduleEntry) to generate a message display
     * @return the message to be used to display the event in it's associated Discord channel
     */
    public static Message generate(ScheduleEntry se)
    {
        JDA jda = Main.getShardManager().getJDA(se.getGuildId());

        String titleUrl = (se.getTitleUrl() != null && VerifyUtilities.verifyUrl(se.getTitleUrl())) ?
                se.getTitleUrl() : "https://nmathe.ws/bots/saber";
        String titleImage = "https://upload.wikimedia.org/wikipedia/en/8/8d/Calendar_Icon.png";
        String footerStr = "ID: " + Integer.toHexString(se.getId());

        if(se.isQuietEnd() || se.isQuietStart() || se.isQuietRemind())
        {
            footerStr += " |";
            if(se.isQuietStart()) footerStr += " quiet-start";
            if(se.isQuietEnd()) footerStr += " quiet-end";
            if(se.isQuietRemind()) footerStr += " quiet-remind";
        }

        // generate reminder footer
        List<Date> reminders = se.getReminders();
        if (!reminders.isEmpty())
        {
            footerStr += " | remind in ";
            long minutes = Instant.now().until(reminders.get(0).toInstant(), ChronoUnit.MINUTES);
            if(minutes<=120)
            {
                footerStr += " " + minutes + "m";
            }
            else
            {
                footerStr += " " + (int) Math.ceil(minutes/60) + "h";
            }
            for (int i=1; i<reminders.size()-1; i++)
            {
                minutes = Instant.now().until(reminders.get(i).toInstant(), ChronoUnit.MINUTES);
                if(minutes<=120)
                {
                    footerStr += ", " + minutes + "m";
                }
                else
                {
                    footerStr += ", " + (int) Math.ceil(minutes/60) + "h";
                }
            }
            if (reminders.size()>1)
            {
                minutes = Instant.now().until(reminders.get(reminders.size()-1).toInstant(), ChronoUnit.MINUTES);
                footerStr += " and ";
                if(minutes<=120)
                {
                    footerStr += minutes + "m";
                }
                else
                {
                    footerStr += (int) Math.ceil(minutes/60) + "h";
                }
            }
        }

        // get embed color from first hoisted bot role
        Color color = Color.DARK_GRAY;

        List<Role> roles = new ArrayList<>(jda.getGuildById(se.getGuildId()).getMember(jda.getSelfUser()).getRoles());
        while(!roles.isEmpty())
        {
            if(roles.get(0).isHoisted())
            {
                color = roles.get(0).getColor();
                break;
            }
            else
            {
                roles.remove(0);
            }
        }

        /*  Invert color if event has started
        if(Instant.now().isAfter(start.toInstant()))
        {
            color = new Color(255-color.getRed(), 255-color.getGreen(), 255-color.getBlue());
        }
        */

        // generate the body of the embed
        String bodyContent;
        if(Main.getScheduleManager().getStyle(se.getChannelId()).toLowerCase().equals("narrow"))
        {
            bodyContent = generateBodyNarrow(se);
        }
        else
        {
            bodyContent = generateBodyFull(se);
        }


        // build the embed
        EmbedBuilder builder = new EmbedBuilder();
        builder.setDescription(bodyContent)
                .setColor(color)
                .setAuthor(se.getTitle(), titleUrl, titleImage)
                .setFooter(footerStr, null);

        if(se.getImageUrl() != null && VerifyUtilities.verifyUrl(se.getImageUrl()))
        {
            builder.setImage(se.getImageUrl());
        }
        if(se.getThumbnailUrl() != null && VerifyUtilities.verifyUrl(se.getThumbnailUrl()))
        {
            builder.setThumbnail(se.getThumbnailUrl());
        }

        return new MessageBuilder().setEmbed(builder.build()).build();
    }


    /**
     * Generates the body content of the discord message for events using the
     * "full" display style
     * @param se the ScheduleEntry Object represented by the display
     * @return the body content as a string
     */
    private static String generateBodyFull(ScheduleEntry se)
    {
        String msg = "";

        // create the upper code block containing the event start/end/repeat/expire info
        String timeLine = genTimeLine(se);
        String repeatLine = "> " + getRepeatString(se.getRepeat(), false) + "\n";
        if(se.getExpire() != null)
        {
            repeatLine += "> expires " + se.getExpire().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                    " " + se.getExpire().getDayOfMonth() + ", " + se.getExpire().getYear() + "\n";
        }
        msg += "```Markdown\n\n" + timeLine + repeatLine + "```\n";

        // insert each comment line with a gap line
        for( String comment : se.getComments() )
        {
            // code blocks in comments must be close within the comment
            int code = StringUtils.countMatches("```", comment);
            if((code%2) == 1)
            {
                msg += comment + " ```" + "\n";
            }
            else
            {
                msg += comment + "\n\n";
            }
        }

        // generate the lower code block
        String zoneLine = "[" + se.getStart().getZone().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "]" +
                MessageGenerator.genTimer(se.getStart(), se.getEnd()) + "\n";

        // if rsvp is enabled, show the number of rsvp
        if(Main.getScheduleManager().isRSVPEnabled(se.getChannelId()))
        {
            String rsvpLine = "- ";
            Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
            for(String key : options.keySet()) // I iterate over the keys rather than the values to keep a order consistent with reactions
            {
                String type = options.get(key);
                rsvpLine += "<" + type + " " + se.getRsvpMembersOfType(type).size() +
                        (se.getRsvpLimit(type)>=0 ? "/"+se.getRsvpLimit(type)+"> " : "> ");
            }

            if(se.getDeadline()!=null)
            {
                rsvpLine += "\n- RSVP closes on " + se.getDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".";
            }


            msg += "```Markdown\n\n" + zoneLine + rsvpLine + "```";
        }
        else
        {
            msg += "```Markdown\n\n" + zoneLine + "```";
        }

        return msg;
    }


    /**
     * Generates the body content of the discord message for events using the
     * "narrow" display style
     * @param se the ScheduleEntry Object represented by the display
     * @return the body content as a string
     */
    private static String generateBodyNarrow(ScheduleEntry se)
    {
        // create the first line of the body
        String timeLine = genTimeLine(se);

        // create the second line of the body
        String lineTwo = "[" + se.getStart().getZone().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                "](" + getRepeatString(se.getRepeat(), true) + ")";

        // if rsvp is enabled, show the number of rsvps
        if(Main.getScheduleManager().isRSVPEnabled(se.getChannelId()))
        {
            String rsvpLine = "";
            Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
            for(String key : options.keySet())  // I iterate over the keys rather than the values to keep a order consistent with reactions
            {
                String type = options.get(key);
                rsvpLine += "<" + type.charAt(0) + " " + se.getRsvpMembersOfType(type).size() +
                        (se.getRsvpLimit(type)>=0 ? "/"+se.getRsvpLimit(type)+"> " : "> ");
            }

            lineTwo += rsvpLine;
        }
        else
        {
            lineTwo += "\n";
        }

        return "```Markdown\n\n" + timeLine + lineTwo + "```\n";
    }


    /**
     * Generates the line of text which indicates the time the event begins and ends
     * Used by both generateBody...() methods
     * @param se the ScheduleEntry Object represented by the display
     * @return the body content as a string
     */
    private static String genTimeLine(ScheduleEntry se)
    {
        String timeFormatter;
        if(Main.getScheduleManager().getClockFormat(se.getChannelId()).equals("24"))
            timeFormatter = "H:mm";
        else
            timeFormatter = "h:mm a";


        String dash = "\u2014";
        String timeLine = "< " + se.getStart().format(DateTimeFormatter.ofPattern("MMM d"));

        // event starts and ends at the same time
        if( se.getStart().until(se.getEnd(), ChronoUnit.SECONDS)==0 )
        {
            timeLine += ", " + se.getStart().format(DateTimeFormatter.ofPattern(timeFormatter)) + " >\n";
        }
        // time span is greater than 1 day
        else if( se.getStart().until(se.getEnd(), ChronoUnit.DAYS)>=1 )
        {
            // all day events
            if( se.getStart().toLocalTime().equals(LocalTime.MIN) && se.getStart().toLocalTime().equals(LocalTime.MIN) )
            {
                timeLine += " " + dash + " " + se.getEnd().format(DateTimeFormatter.ofPattern("MMM d")) + " >\n";
            }
            else // all other events
            {
                timeLine += ", " + se.getStart().format(DateTimeFormatter.ofPattern(timeFormatter)) +
                        " " + dash + " " + se.getEnd().format(DateTimeFormatter.ofPattern("MMM d")) + ", " +
                        se.getEnd().format(DateTimeFormatter.ofPattern(timeFormatter)) + " >\n";
            }
        }
        // time span is within 1 day
        else
        {
            timeLine += ", " + se.getStart().format(DateTimeFormatter.ofPattern(timeFormatter)) +
                    " " + dash + " " + se.getEnd().format(DateTimeFormatter.ofPattern(timeFormatter)) + " >\n";
        }

        return timeLine;
    }


    /**
     * Generated a string describing the repeat settings of an event
     * @param bitset (int) representing a set of bits where each bit of the digit
     *               represents has distinct meaning when it comes to an event's recurrence
     * @param isNarrow (boolean) for use with narrow style events
     * @return string
     */
    public static String getRepeatString(int bitset, boolean isNarrow)
    {
        String str;
        if(isNarrow)
        {
            if( bitset == 0 )
                return "once";
            if( bitset == 0b1111111 || bitset == 0b10000001)
                return "every day";

            // yearly repeat
            if((bitset & 0b100000000) == 0b100000000)
            {
                return "every year";
            }

            // repeat on interval
            if((bitset & 0b10000000) == 0b10000000 )
            {
                Integer interval = (0b10000000 ^ bitset);
                String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
                return "every " + (interval>spellout.length ? interval : spellout[interval-1]) + " days";
            }

            // repeat on fixed days
            str = "every ";
        }
        else
        {
            if( bitset == 0 )
                return "does not repeat";
            if( bitset == 0b1111111 )
                return "repeats daily";

            // yearly repeat
            if((bitset & 0b100000000) == 0b100000000)
            {
                return "repeats yearly";
            }

            // repeat on interval
            if((bitset & 0b10000000) == 0b10000000 )
            {
                Integer interval = (0b10000000 ^ bitset);
                String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
                return "repeats every " + (interval>spellout.length ? interval : spellout[interval-1]) + " days";
            }

            // repeat on fixed days
            str = "repeats weekly on ";
        }

        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0000001)
                return str + "Sunday";

            str += "Sun";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0000010)
                return str + "Monday";

            str += "Mon";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0000100)
                return str + "Tuesday";

            str += "Tue";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0001000)
                return str + "Wednesday";

            str += "Wed";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0010000)
                return str + "Thursday";

            str += "Thu";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b0100000)
                return str + "Friday";

            str += "Fri";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        bitset = bitset>>1;
        if( (bitset & 1) == 1 )
        {
            if(bitset==0b1000000)
                return str + "Saturday";

            str += "Sat";
            if( (bitset>>1) != 0 )
                str += ", ";
        }
        return str;
    }


    /**
     * Generated a string describing the current time left before an event begins or ends
     * @param start the start time of event
     * @param end the end time of event
     * @return String
     */
    private static String genTimer(ZonedDateTime start, ZonedDateTime end)
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
                //timer += "within the hour.)";
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
                int daysTil = (int) ChronoUnit.DAYS.between(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS),
                        start.truncatedTo(ChronoUnit.DAYS));
                if( daysTil <= 1)
                    timer += "tomorrow.)";
                else
                    timer += "in " + daysTil + " days.)";
            }
        }
        else // if the event has started
        {
            timer = "(ends ";
            if( timeTilEnd < 60*60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
                if( minutesTil <= 1)
                    timer += "in a minute.)";
                else
                    timer += "in " + minutesTil + " minutes.)";
                //timer += "within one hour.)";
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
                int daysTil = (int) ChronoUnit.DAYS.between(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS), end.truncatedTo(ChronoUnit.DAYS));
                if( daysTil <= 1)
                    timer += "tomorrow.)";
                else
                    timer += "in " + daysTil + " days.)";
            }
        }
        return timer;
    }
}
