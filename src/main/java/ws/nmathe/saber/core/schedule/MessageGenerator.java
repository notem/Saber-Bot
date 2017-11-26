package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Role;
import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.Main;
import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.utils.ParsingUtilities;
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
        String footerStr = "ID: " + ParsingUtilities.intToEncodedID(se.getId());

        if(se.isQuietEnd() || se.isQuietStart() || se.isQuietRemind())
        {
            footerStr += " |";
            if(se.isQuietStart()) footerStr += " quiet-start";
            if(se.isQuietEnd()) footerStr += " quiet-end";
            if(se.isQuietRemind()) footerStr += " quiet-remind";
        }

        // generate reminder footer
        List<Date> reminders = new ArrayList<>();
        reminders.addAll(se.getReminders());
        reminders.addAll(se.getEndReminders());
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
        List<Role> roles = new ArrayList<>(
                        jda.getGuildById(se.getGuildId())
                                .getMember(jda.getSelfUser())
                                .getRoles());
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
        String repeatLine = "> " + se.getRecurrence().toString(false) + "\n";
        if(se.getExpire() != null)
        {
            repeatLine += "> expires " + se.getExpire().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                    " " + se.getExpire().getDayOfMonth() + ", " + se.getExpire().getYear() + "\n";
        }
        msg += "```Markdown\n\n" + timeLine + repeatLine +
                (se.getLocation()==null ? "":"<Location: "+se.getLocation()+">\n")+"```\n";

        // insert each comment line with a gap line
        for( String comment : se.getComments() )
        {
            // don't break the maximum length of messages
            if(msg.length() > 1800) break;

            // code blocks in comments must be closed
            int code = StringUtils.countMatches("```", comment);
            if((code%2) == 1) msg += ParsingUtilities.parseMessageFormat(comment, se, false) + " ```" + "\n";
            else msg += ParsingUtilities.parseMessageFormat(comment, se, false) + "\n\n";
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
            if(se.getDeadline() != null)
            {
                rsvpLine += "\n+ RSVP closes " + se.getDeadline().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                    " " + se.getDeadline().getDayOfMonth() + ", " + se.getDeadline().getYear() + ".";
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
                "](" + se.getRecurrence().toString(true) + ")";

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
            lineTwo += "\n"+rsvpLine;
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
                    timer += "in a minute)";
                else
                    timer += "in " + minutesTil + " minutes)";
                //timer += "within the hour.)";
            }
            else if( timeTilStart < 24 * 60 * 60 )
            {
                int hoursTil = (int)Math.ceil((double)timeTilStart/(60*60));
                if( hoursTil <= 1)
                    timer += "in the hour)";
                else
                    timer += "in " + hoursTil + " hours)";
            }
            else
            {
                int daysTil = (int) ChronoUnit.DAYS.between(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS),
                        start.truncatedTo(ChronoUnit.DAYS));
                if( daysTil <= 1)
                    timer += "tomorrow)";
                else
                    timer += "in " + daysTil + " days)";
            }
        }
        else // if the event has started
        {
            timer = "(ends ";
            if( timeTilEnd < 60*60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
                if( minutesTil <= 1)
                    timer += "in a minute)";
                else
                    timer += "in " + minutesTil + " minutes)";
                //timer += "within one hour.)";
            }

            else if( timeTilEnd < 24 * 60 * 60 )
            {
                int hoursTil = (int)Math.ceil((double)timeTilEnd/(60*60));
                if( hoursTil <= 1)
                    timer += "in one hour)";
                else
                    timer += "in " + hoursTil + " hours)";
            }
            else
            {
                int daysTil = (int) ChronoUnit.DAYS
                        .between(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS), end.truncatedTo(ChronoUnit.DAYS));
                if( daysTil <= 1)
                    timer += "tomorrow)";
                else
                    timer += "in " + daysTil + " days)";
            }
        }
        return timer;
    }
}
