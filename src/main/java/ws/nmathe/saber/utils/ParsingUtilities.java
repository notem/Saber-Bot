package ws.nmathe.saber.utils;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * utilities which do some sort of string parsing
 */
public class ParsingUtilities
{
    /**
     * parses a local time string inputted by a user into a ZonedDateTime object
     * @param userInput the local time
     * @return new ZonedDateTime with the new time
     */
    public static LocalTime parseTime(String userInput)
    {
        LocalTime time;
        // relative time
        if(userInput.matches(".+[mM][iI][nN]$"))
        {
            time = LocalTime.now().plusMinutes(Long.parseLong(userInput.replaceAll("[^\\d]", "")));
        }
        // absolute time
        else if(userInput.equals("24:00") || userInput.equals("24")) // 24:00 is not really a valid time
        {
            time = LocalTime.MAX;
        }
        else if(userInput.contains(":")) // 1:00pm or 13:00
        {
            if(userInput.matches(".+([aApP][mM])"))
            {
                time = LocalTime.parse(userInput.toUpperCase(), DateTimeFormatter.ofPattern("h:mma"));
            }
            else
            {
                time = LocalTime.parse(userInput, DateTimeFormatter.ofPattern("H:mm"));
            }
        }
        else // 1pm or 13
        {
            if(userInput.matches(".+([aApP][mM])"))
            {
                time = LocalTime.parse(userInput.toUpperCase(), DateTimeFormatter.ofPattern("ha"));
            }
            else
            {
                time = LocalTime.parse(userInput, DateTimeFormatter.ofPattern("H"));
            }
        }
        return time;
    }

    /**
     * @param format the base string to parse into a message
     * @param entry the entry associated with the message
     * @return a new message which has entry specific information inserted into the format string
     */
    public static String parseMessageFormat(String format, ScheduleEntry entry, boolean displayComments)
    {
        // determine time formatter from schedule settings
        String clock = Main.getScheduleManager().getClockFormat(entry.getChannelId());
        DateTimeFormatter timeFormatter;
        if(clock.equalsIgnoreCase("12"))
             timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        else
             timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // advanced parsing
        /*
         * parses the format string using regex grouping
         * allows for an 'if element exists, print string + element + string' type of insertion
         */
        Matcher matcher = Pattern.compile("%\\{(.*?)}").matcher(format);
        while(matcher.find())
        {
            String group = matcher.group();
            String trimmed = group.substring(2, group.length()-1);
            StringBuilder sub = new StringBuilder();
            if(!trimmed.isEmpty())
            {
                Matcher matcher2 = Pattern.compile("\\[.*?]").matcher(trimmed);
                if(trimmed.matches("(\\[.*?])?c\\d+(\\[.*?])?") && displayComments) // advanced comment
                {
                    int i = Integer.parseInt(trimmed.replaceAll("(\\[.*?])?c|\\[.*?]", ""));
                    if(entry.getComments().size() >= i && i > 0)
                    {
                        sub.append(messageFormatHelper(entry.getComments().get(i - 1), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?s(\\[.*?])?")) // advanced start
                {
                    if(!entry.hasStarted())
                    {
                        while(matcher2.find())
                        {
                            sub.append(matcher2.group().replaceAll("[\\[\\]]", ""));
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?e(\\[.*?])?")) // advanced end
                {
                    if(entry.hasStarted())
                    {
                        while(matcher2.find())
                        {
                            sub.append(matcher2.group().replaceAll("[\\[\\]]", ""));
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?start .+(\\[.*?])?")) // advanced end
                {
                    String formatter = trimmed.replaceAll("start ","")
                            .replaceAll("^[GuyDMLdQqYwWEeCFahkKHmsSAnNVzOXxZp'\\[\\]#{}.,\\- ]"," ")
                            .replaceAll("\\[.*?]","");
                    String startString = "";
                    try {
                        startString = entry.getStart().format(DateTimeFormatter.ofPattern(formatter));
                    } catch(Exception ignored) {}
                    sub.append(startString);
                }
                else if(trimmed.matches("(\\[.*?])?end .+(\\[.*?])?")) // advanced end
                {
                    String formatter = trimmed.replaceAll("end ","")
                            .replaceAll("^[GuyDMLdQqYwWEeCFahkKHmsSAnNVzOXxZp'\\[\\]#{}.,\\- ]"," ")
                            .replaceAll("\\[.*?]","");
                    String endString = "";
                    try {
                        endString = entry.getEnd().format(DateTimeFormatter.ofPattern(formatter));
                    } catch(Exception ignored) {}
                    sub.append(endString);
                }
                else if(trimmed.matches("(\\[.*?])?m(\\[.*?])?")) // advanced remind in minutes
                {
                    if(!entry.hasStarted())
                    {
                        long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            sub.append(messageFormatHelper("" + (minutes + 1), matcher2));
                        }
                    }
                    else
                    {
                        long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            sub.append(messageFormatHelper(""+(minutes+1), matcher2));
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?h(\\[.*?])?")) // advanced remind in hours
                {
                    if(!entry.hasStarted())
                    {
                        long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            sub.append(messageFormatHelper(""+(minutes+1)/60, matcher2));
                        }
                    }
                    else
                    {
                        long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            sub.append(messageFormatHelper(""+(minutes+1)/60, matcher2));
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?rsvp .+(\\[.*?])?")) // rsvp count
                {
                    String name = trimmed.replaceAll("rsvp ","").replaceAll("\\[.*?]","");
                    List<String> members = entry.getRsvpMembers().get(name);
                    if(members != null)
                    {
                        sub.append(messageFormatHelper(""+members.size(), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?mention .+(\\[.*?])?")) // rsvp mentions
                {
                    String name = trimmed.replaceAll("mention ","").replaceAll("\\[.*?]","");
                    List<String> users = compileUserList(entry, name);
                    if (users != null)  // a valid mention option was used
                    {
                        StringBuilder userMentions = new StringBuilder();
                        for(int i=0; i<users.size(); i++)
                        {
                            if (users.get(i).matches("\\d+"))
                                userMentions.append("<@").append(users.get(i)).append(">");
                            if (i+1<users.size())
                                userMentions.append(", ");
                        }
                        sub.append(messageFormatHelper(userMentions.toString(), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?mentionm .+(\\[.*?])?")
                        || trimmed.matches("(\\[.*?])?list .+(\\[.*?])?")) // rsvp mentions
                {
                    String name = trimmed.replace("mentionm ","").replace("list","").replaceAll("\\[.*?]","");
                    List<String> users = compileUserList(entry, name);
                    if (users != null)
                    {
                        StringBuilder userMentions = new StringBuilder();
                        for(int i=0; i<users.size(); i++)
                        {
                            if (users.get(i).matches("\\d+"))
                            {
                                Member member = Main.getShardManager().getJDA(entry.getGuildId())
                                        .getGuildById(entry.getGuildId()).getMemberById(users.get(i));
                                userMentions.append(member.getEffectiveName());
                            }
                            if (i+1<users.size())
                                userMentions.append(", ");
                        }
                        sub.append(messageFormatHelper(userMentions.toString(), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?u(\\[.*?])?")) // advanced title url
                {
                    if(entry.getTitleUrl() != null)
                    {
                        sub.append(messageFormatHelper(entry.getTitleUrl(), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?v(\\[.*?])?")) // advanced image url
                {
                    if(entry.getImageUrl() != null)
                    {
                        sub.append(messageFormatHelper(entry.getImageUrl(), matcher2));
                    }
                }
                else if(trimmed.matches("(\\[.*?])?w(\\[.*?])?")) // advanced thumbnail url
                {
                    if(entry.getThumbnailUrl() != null)
                    {
                        sub.append(messageFormatHelper(entry.getThumbnailUrl(), matcher2));
                    }
                }
            }
            format = format.replace(group, sub.toString());
        }

        // legacy parsing
        /*
         * parses the format string character by character looking for % characters
         * a token is one % character followed by a key character
         */
        StringBuilder announceMsg = new StringBuilder();
        for( int i = 0; i < format.length(); i++ )
        {
            char ch = format.charAt(i);
            if(ch == '%' && i+1 < format.length())
            {
                i++;
                ch = format.charAt(i);
                switch(ch)
                {
                    case 'c' :
                        if(i+1 < format.length() && displayComments)
                        {
                            ch = format.charAt(i+1);
                            if( Character.isDigit( ch ) )
                            {
                                i++;
                                int x = Integer.parseInt("" + ch);
                                if(entry.getComments().size()>=x && x!=0)
                                {
                                    String parsedComment = ParsingUtilities.parseMessageFormat(entry.getComments().get(x - 1), entry, false);
                                    announceMsg.append(parsedComment);
                                }
                            }
                        }
                        break;
                    case 'f' :
                        if(displayComments)
                        {   // if this call of the parser is nested, don't insert comments
                            announceMsg.append(String.join("\n", entry.getComments().stream()
                                    .map(comment -> ParsingUtilities.parseMessageFormat(comment, entry, false))
                                    .collect(Collectors.toList())));
                        }
                        break;
                    case 'a' :
                        if(!entry.hasStarted())
                        {
                            announceMsg.append("begins");
                            long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                            if(minutes>0)
                            {
                                if(minutes > 120)
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hours");
                                else
                                    announceMsg.append(" in ").append(minutes + 1).append(" minutes");
                            }
                        } else
                        {
                            announceMsg.append("ends");
                            long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                            if(minutes>0)
                            {
                                if(minutes > 120)
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hours");
                                else
                                    announceMsg.append(" in ").append(minutes + 1).append(" minutes");
                            }
                        }
                        break;
                    case 'b' :
                        if( !entry.hasStarted() )
                            announceMsg.append("begins");
                        else
                            announceMsg.append("ends");
                        break;
                    case 'x' :
                        if(!entry.hasStarted())
                        {
                            long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                            if(minutes>0)
                            {
                                if(minutes > 120)
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hours");
                                else
                                    announceMsg.append(" in ").append(minutes + 1).append(" minutes");
                            }
                        }
                        else
                        {
                            long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                            if(minutes>0)
                            {
                                if(minutes > 120)
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hours");
                                else
                                    announceMsg.append(" in ").append(minutes + 1).append(" minutes");
                            }
                        }
                        break;
                    case 's':
                        announceMsg.append(entry.getStart().format(timeFormatter));
                        break;
                    case 'e':
                        announceMsg.append(entry.getEnd().format(timeFormatter));
                        break;
                    case 't' :
                        announceMsg.append(entry.getTitle());
                        break;
                    case 'd' :
                        announceMsg.append(String.format("%02d",entry.getStart().getDayOfMonth()));
                        break;
                    case 'D' :
                        announceMsg.append(StringUtils.capitalize(entry.getStart().getDayOfWeek().toString()));
                        break;
                    case 'm' :
                        announceMsg.append(String.format("%02d",entry.getStart().getMonthValue()));
                        break;
                    case 'M' :
                        announceMsg.append(StringUtils.capitalize(entry.getStart().getMonth().toString()));
                        break;
                    case 'y' :
                        announceMsg.append(entry.getStart().getYear());
                        break;
                    case 'i':
                        announceMsg.append(ParsingUtilities.intToEncodedID(entry.getId()));
                        break;
                    case '%' :
                        announceMsg.append('%');
                        break;
                    case 'u' :
                        announceMsg.append(entry.getTitleUrl() == null ? "" : entry.getTitleUrl());
                        break;
                    case 'v' :
                        announceMsg.append(entry.getImageUrl() == null ? "" : entry.getImageUrl());
                        break;
                    case 'w':
                        announceMsg.append(entry.getThumbnailUrl() == null ? "" : entry.getThumbnailUrl());
                        break;
                    case 'n':
                        announceMsg.append("\n");
                        break;
                    case 'h':
                        announceMsg.append(String.format("%02d",entry.getStart().getHour()));
                        break;
                    case 'k':
                        announceMsg.append(String.format("%02d",entry.getStart().getMinute()));
                        break;
                    case 'l':
                        announceMsg.append(entry.getLocation());
                        break;
                }
            }
            else
            {
                announceMsg.append(ch);
            }
        }

        return announceMsg.toString();
    }

    /**
     * aids parseMessageFormat() in parsing strings for advanced substitution
     */
    private static String messageFormatHelper(String insert, Matcher matcher)
    {
        String str = "";
        if(matcher.find())
            str += matcher.group().replaceAll("[\\[\\]]", "");
        str += insert;
        if(matcher.find())
            str += matcher.group().replaceAll("[\\[\\]]", "");
        return str;
    }


    /**
     * generates a list of user IDs for a given RSVP category of an event
     * @param entry ScheduleEntry object
     * @param name name of RSVP category
     * @return List of Stings or null if category is invalid
     */
    private static List<String> compileUserList(ScheduleEntry entry, String name)
    {
        List<String> users = null;
        if (name.toLowerCase().equals("no-input"))
        {
            List<String> rsvped = new ArrayList<>();
            Set<String> keys = entry.getRsvpMembers().keySet();
            for(String key : keys)
            {
                rsvped.addAll(entry.getRsvpMembersOfType(key));
            }
            JDA shard = Main.getShardManager().getJDA(entry.getGuildId());
            Guild guild = shard.getGuildById(entry.getGuildId());
            Channel channel = shard.getTextChannelById(entry.getChannelId());
            users = guild.getMembers().stream()
                    .filter(member -> member.getPermissions(channel).contains(Permission.MESSAGE_READ))
                    .map(member -> member.getUser().getId())
                    .filter(memberId -> !rsvped.contains(memberId)).collect(Collectors.toList());
        } else
        {
            List<String> members = entry.getRsvpMembers().get(name);
            if(members != null) users = entry.getRsvpMembersOfType(name);
        }
        return users;
    }


    /**
     * Parses user supplied input for information indicating the reminder intervals to use for a schedule's
     * reminder settings
     * @param arg (String) user input
     * @return (Set<Integer>) a linked set of integers representing the time (in minutes) before an event starts
     */
    public static Set<Integer> parseReminder(String arg)
    {
        Set<Integer> list = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[-]?\\d+[^\\d]?").matcher(arg);
        while(matcher.find())
        {
            String group = matcher.group();
            Character ch = group.charAt(group.length()-1);
            if(Character.isDigit(ch))
            {
                if(VerifyUtilities.verifyInteger(group))
                {
                    list.add(Integer.parseInt(group));
                }
            }
            else if(VerifyUtilities.verifyInteger(group.substring(0, group.length()-1)))
            {
                Integer units = Integer.parseInt(group.substring(0, group.length()-1));
                switch(ch)
                {
                    case 'h':
                        list.add(units*60);
                        break;
                    case 'd':
                        list.add(units*60*24);
                        break;
                    case 'm':
                    default:
                        list.add(units);
                        break;
                }
            }
        }
        return list;
    }


    /**
     * Parses user input for a date information
     * @param arg (String)
     */
    public static ZonedDateTime parseDate(String arg, ZoneId zone)
    {
        switch (arg)
        {
            case "today":
                return ZonedDateTime.now(zone);

            case "tomorrow":
                return ZonedDateTime.now(zone).plusDays(1);

            case "overmorrow":
                return ZonedDateTime.now(zone).plusDays(2);

            default:
                String[] splt = arg.split("[^0-9]+");
                LocalDate date = LocalDate.now().plusDays(1);
                if(splt.length == 1)
                {
                    date = LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), Integer.parseInt(splt[0]));
                }
                else if(splt.length == 2)
                {
                    date = LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(splt[0]), Integer.parseInt(splt[1]));
                }
                else if(splt.length == 3)
                {
                    date = LocalDate.of(Integer.parseInt(splt[0]), Integer.parseInt(splt[1]), Integer.parseInt(splt[2]));
                }
                return ZonedDateTime.of(date, LocalTime.MAX, zone);
        }
    }


    /**
     * Parses user supplied input for zone information
     * Allows for zone names to be inputted without proper capitalization
     * @param userInput (String) user input
     * @return (ZoneId) zone information as parsed
     */
    public static ZoneId parseZone(String userInput)
    {
        for(String validZone : ZoneId.getAvailableZoneIds())
        {
            if(validZone.equalsIgnoreCase(userInput))
            {
                return ZoneId.of(validZone);
            }
        }
        return ZoneId.of(Main.getBotSettingsManager().getTimeZone());
    }


    /**
     * Parses out expire and deadline keyword input
     */
    public static ZonedDateTime parseNullableDate(String input, ZoneId zone)
    {
        switch(input)
        {
            case "off":
            case "none":
            case "never":
            case "null":
                return null;
            default:
                return ParsingUtilities.parseDate(input, zone);
        }
    }

    /**
     * Parses out url, image, and thumbnail keyword arguments
     */
    public static String parseUrl(String input)
    {
        switch(input)
        {
            case "null":
            case "off":
            case "none":
                return null;
            default:
                return input;
        }
    }

    /**
     * evaluates a announcement override 'time string' into a definite ZonedDateTime object
     * @param time 'time string'
     * @param se ScheduleEntry the override resides on
     * @return ZonedDateTime for announcement override, or null if un-parsable
     */
    public static ZonedDateTime parseTimeString(String time, ScheduleEntry se)
    {
        time = time.toUpperCase();  // all caps

        // determine basis for the announcement time
        ZonedDateTime announcementTime;
        if(time.startsWith("START"))
        {
            time = time.replace("START", "");
            announcementTime = se.getStart();
        }
        else if(time.startsWith("END"))
        {
            time = time.replace("END", "");
            announcementTime = se.getEnd();
        }
        else
        {
            return null;
        }

        // determine if offset is positive or negative
        boolean positive;
        if(time.startsWith("+"))
        {
            time = time.replace("+", "");
            positive = true;
        }
        else if(time.startsWith("-"))
        {
            time = time.replace("-", "");
            positive = false;
        }
        else
        {
            return announcementTime;
        }

        // parse out the time offset
        Integer minutes = time.replaceAll("[^\\d]", "").isEmpty() ?
                0 : Integer.parseInt(time.replaceAll("[^\\d]", ""));
        if (minutes != 0)
        {
            switch(time.charAt(time.length()-1))
            {
                case 'H':
                    minutes = minutes*60;
                    break;
                case 'D':
                    minutes = 60*24;
                    break;
            }
        }

        // add offset to the time and return
        if(positive) return announcementTime.plusMinutes(minutes);
        else return announcementTime.minusMinutes(minutes);
    }

    /**
     * @param input user-supplied encoded string (Character.RADIX_MAX)
     * @return int representation of the base64 string
     */
    public static int encodeIDToInt(String input)
    {
        return Integer.parseInt(input, Character.MAX_RADIX);
    }

    /**
     * @param input integer representing an event's ID
     * @return Character.RADIX_MAX encoded representation of the integer
     */
    public static String intToEncodedID(int input)
    {
        return Integer.toString(input, Character.MAX_RADIX);
    }
}
