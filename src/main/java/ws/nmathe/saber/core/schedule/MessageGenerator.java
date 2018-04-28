package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Role;
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
    private static String DEFAULT_URL = "https://nmathe.ws/bots/saber";
    private static String ICON_URL = "https://upload.wikimedia.org/wikipedia/en/8/8d/Calendar_Icon.png";

    /**
     * Primary method which generates a complete Discord message object for the event
     * @param se (ScheduleEntry) to generate a message display
     * @return the message to be used to display the event in it's associated Discord channel
     */
    public static Message generate(ScheduleEntry se)
    {
        // prepare title
        String titleUrl = (se.getTitleUrl() != null && VerifyUtilities.verifyUrl(se.getTitleUrl())) ?
                se.getTitleUrl() : DEFAULT_URL;
        String titleImage = ICON_URL;

        // generate the footer
        String footerStr = generateFooter(se);

        // determine the embed color
        Color embedColor = generateColor(se);

        // generate the body of the embed
        String bodyContent;
        String style = Main.getScheduleManager().getStyle(se.getChannelId());
        if(style.equalsIgnoreCase("narrow"))
        {
            bodyContent = generateBodyNarrow(se);
        }
        else
        {
            bodyContent = generateBodyFull(se);
        }

        // prepare the embed
        EmbedBuilder builder = new EmbedBuilder();
        builder.setDescription(bodyContent.substring(0,Math.min(bodyContent.length(), 2048)))
                .setColor(embedColor)
                .setAuthor(se.getTitle(), titleUrl, titleImage)
                .setFooter(footerStr.substring(0,Math.min(footerStr.length(), 2048)), null);

        // add the image and thumbnail url links (if valid)
        if(se.getImageUrl() != null && VerifyUtilities.verifyUrl(se.getImageUrl()))
        {
            builder.setImage(se.getImageUrl());
        }
        if(se.getThumbnailUrl() != null && VerifyUtilities.verifyUrl(se.getThumbnailUrl()))
        {
            builder.setThumbnail(se.getThumbnailUrl());
        }

        // return the fully constructed message
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
        StringBuilder msg = new StringBuilder();

        String timeLine = genTimeLine(se);
        String repeatLine = "> " + se.getRecurrence().toString(false) + "\n";

        //
        // create the upper code block
        //
        if(se.getExpire() != null)
        {   // expire information on separate line
            repeatLine += "> expires " + se.getExpire().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                    " " + se.getExpire().getDayOfMonth() + ", " + se.getExpire().getYear() + "\n";
        }
        else if(se.getRecurrence().getCount() != null)
        {   // remaining event occurrences on separate line
            repeatLine += "> occurs " + se.getRecurrence().countRemaining(se.getStart()) + " more times\n";
        }
        // append block lines
        msg.append("```Markdown\n\n")
                .append(timeLine)
                .append(repeatLine)
                .append(se.getLocation() == null ? "" : "<Location: " + se.getLocation() + ">\n")
                .append("```\n");

        //
        // insert the event description
        //
        msg.append(ParsingUtilities.processText(se.getDescription(), se, true))
                .append("\n");

        //
        // generate the lower code block
        //
        String zoneLine = "[" + se.getStart().getZone().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "]" +
                MessageGenerator.genTimer(se.getStart(), se.getEnd()) + "\n";

        // if rsvp is enabled, show the number of rsvp
        StringBuilder rsvpLine = new StringBuilder("");
        if(Main.getScheduleManager().isRSVPEnabled(se.getChannelId()))
        {
            rsvpLine.append("- ");
            Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
            for(String emoji : options.keySet()) // I iterate over the keys rather than the values to keep a order consistent with reactions
            {
                String type = options.get(emoji);
                if (se.getRsvpLimit(type) != 0) // don't list the rsvp options on the event
                {
                    rsvpLine.append("<")
                            .append(type)
                            .append(" ")
                            .append(se.getRsvpMembersOfType(type).size())
                            .append(se.getRsvpLimit(type) >= 0 ? "/" + se.getRsvpLimit(type) + "> " : "> ");
                }
            }
            if(se.getDeadline() != null)
            {
                rsvpLine.append("\n+ RSVP closes ")
                        .append(se.getDeadline().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                        .append(" ").append(se.getDeadline().getDayOfMonth())
                        .append(", ")
                        .append(se.getDeadline().getYear())
                        .append(".");
            }

        }
        // append block lines
        msg.append("```Markdown\n\n")
                .append(zoneLine)
                .append(rsvpLine)
                .append("```");

        // return full body string contents
        return msg.toString();
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

        // timezone and repeat information
        String expireAndRepeat = "[" + se.getStart().getZone().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                "](" + se.getRecurrence().toString(true) + ")";

        // include event expiration information (if configured)
        if (se.getRecurrence().getCount() != null)
        {   // if event has a count limit, include that information in the display
            expireAndRepeat += "\n+ Expires after <"+se.getRecurrence().countRemaining(se.getStart())+" more times>";
        }
        else if (se.getExpire() != null)
        {   // event expiration date
            expireAndRepeat += "\n+ Expires on <"+se.getExpire().toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))+">\n";
        }

        // if rsvp is enabled, show the number of rsvps
        if(Main.getScheduleManager().isRSVPEnabled(se.getChannelId()))
        {
            StringBuilder rsvpLine = new StringBuilder();
            Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
            // iterate over the keys rather than the values to keep
            // the order consistent with the order reactions are displayed
            for(String emoji : options.keySet())
            {
                String type = options.get(emoji);
                if (se.getRsvpLimit(type) != 0) // don't list the rsvp options on the event
                {
                    rsvpLine.append("<").append(type.charAt(0)).append(" ")
                            .append(se.getRsvpMembersOfType(type).size())
                            .append(se.getRsvpLimit(type) > 0 ? "/" + se.getRsvpLimit(type) + "> " : "> ");
                }
            }
            expireAndRepeat += "\n"+rsvpLine;
        }
        return "```Markdown\n\n" + timeLine + expireAndRepeat + "```\n";
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
        if (se.getStart().until(se.getEnd(), ChronoUnit.SECONDS)==0)
        {
            timeLine += ", " + se.getStart().format(DateTimeFormatter.ofPattern(timeFormatter)) + " >\n";
        }
        // time span is greater than 1 day
        else if (se.getStart().until(se.getEnd(), ChronoUnit.DAYS)>=1)
        {
            // all day events
            if (se.getStart().toLocalTime().equals(LocalTime.MIN) &&
                    se.getStart().toLocalTime().equals(LocalTime.MIN))
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

        if (start.isAfter(ZonedDateTime.now()))
        {
            timer = "(begins ";
            if (timeTilStart < 60 * 60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilStart/(60));
                if (minutesTil <= 1)
                    timer += "in a minute)";
                else
                    timer += "in " + minutesTil + " minutes)";
                //timer += "within the hour.)";
            }
            else if (timeTilStart < 24 * 60 * 60 )
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
                if (daysTil <= 1)
                    timer += "in one day)";
                else
                    timer += "in " + daysTil + " days)";
            }
        }
        else // if the event has started
        {
            timer = "(ends ";
            if (timeTilEnd < 60*60 )
            {
                int minutesTil = (int)Math.ceil((double)timeTilEnd/(60));
                if (minutesTil <= 1)
                    timer += "in a minute)";
                else
                    timer += "in " + minutesTil + " minutes)";
                //timer += "within one hour.)";
            }

            else if (timeTilEnd < 24 * 60 * 60 )
            {
                int hoursTil = (int)Math.ceil((double)timeTilEnd/(60*60));
                if (hoursTil <= 1)
                    timer += "in one hour)";
                else
                    timer += "in " + hoursTil + " hours)";
            }
            else
            {
                int daysTil = (int) ChronoUnit.DAYS
                        .between(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS), end.truncatedTo(ChronoUnit.DAYS));
                if (daysTil <= 1)
                    timer += "in one day)";
                else
                    timer += "in " + daysTil + " days)";
            }
        }
        return timer;
    }


    /**
     * creates the footer text to be added to the message embed
     * @param se ScheduleEntry object
     * @return fully generated footer String
     */
    private static String generateFooter(ScheduleEntry se)
    {
        // initialize footer with ID information
        StringBuilder footerStr = new StringBuilder("ID: " + ParsingUtilities.intToEncodedID(se.getId()));

        // append quiet information to footer
        if(se.isQuietEnd() || se.isQuietStart() || se.isQuietRemind())
        {
            footerStr.append(" |");
            if(se.isQuietStart()) footerStr.append(" quiet-start");
            if(se.isQuietEnd()) footerStr.append(" quiet-end");
            if(se.isQuietRemind()) footerStr.append(" quiet-remind");
        }

        // generate reminder footer
        List<Date> reminders = new ArrayList<>();
        reminders.addAll(se.getReminders());
        reminders.addAll(se.getEndReminders());
        if (!reminders.isEmpty())
        {
            footerStr.append(" | remind in ");
            long minutes = Instant.now().until(reminders.get(0).toInstant(), ChronoUnit.MINUTES);
            if(minutes<=120)
            {
                footerStr.append(" ")
                        .append(minutes)
                        .append("m");
            }
            else
            {
                footerStr.append(" ")
                        .append((int) Math.ceil(minutes / 60))
                        .append("h");
            }
            for (int i=1; i<reminders.size()-1; i++)
            {
                minutes = Instant.now().until(reminders.get(i).toInstant(), ChronoUnit.MINUTES);
                if(minutes<=120)
                {
                    footerStr.append(", ")
                            .append(minutes)
                            .append("m");
                }
                else
                {
                    footerStr.append(", ")
                            .append((int) Math.ceil(minutes / 60))
                            .append("h");
                }
            }
            if (reminders.size()>1)
            {
                minutes = Instant.now().until(reminders.get(reminders.size()-1).toInstant(), ChronoUnit.MINUTES);
                footerStr.append(" and ");
                if(minutes<=120)
                {
                    footerStr.append(minutes)
                            .append("m");
                }
                else
                {
                    footerStr.append((int) Math.ceil(minutes / 60))
                            .append("h");
                }
            }
        }

        // return completed footer
        return footerStr.toString();
    }

    /**
     * creates the color object to be used with the embed
     * @param se ScheduleEntry object
     * @return color
     */
    private static Color generateColor(ScheduleEntry se)
    {
        // attempt to use the ScheduleEntry's color attribute
        Color color = null;
        if (se.getColor() != null)
        {
            color = Color.getColor(se.getColor());
            if (color == null)
            {
                try
                {
                    color = Color.decode(se.getColor());
                }
                catch (NumberFormatException ignored)
                {
                    color = null;
                }
            }
        }

        // if color not yet defined, use color from bot hoisted role
        if(color == null)
        {
            // set default
            color = Color.DARK_GRAY;

            // find JDA shard instance for guild
            JDA jda = Main.getShardManager().getJDA(se.getGuildId());

            // get embed color from first hoisted bot role
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
        }

        // return the color (default DARK_GRAY)
        return color;
    }
}
