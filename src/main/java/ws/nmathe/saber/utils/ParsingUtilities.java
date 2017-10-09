package ws.nmathe.saber.utils;

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
    public static String parseMessageFormat(String format, ScheduleEntry entry)
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
            String sub = "";
            if(!trimmed.isEmpty())
            {
                Matcher matcher2 = Pattern.compile("\\[.*?]").matcher(trimmed);
                if(trimmed.matches("(\\[.*?])?c\\d+(\\[.*?])?")) // advanced comment
                {
                    int i = Integer.parseInt(trimmed.replaceAll("\\[.*?]c?", ""));
                    if(entry.getComments().size() <= i && i > 0)
                    {
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        sub += entry.getComments().get(i);
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                    }
                }
                else if(trimmed.matches("(\\[.*?])?s(\\[.*?])?")) // advanced start
                {
                    if(!entry.hasStarted())
                    {
                        while(matcher2.find())
                        {
                            sub += matcher2.group().replaceAll("[\\[\\]]","");
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?e(\\[.*?])?")) // advanced end
                {
                    if(entry.hasStarted())
                    {
                        while(matcher2.find())
                        {
                            sub += matcher2.group().replaceAll("[\\[\\]]","");
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?m(\\[.*?])?")) // advanced remind in minutes
                {
                    if(!entry.hasStarted())
                    {
                        long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                            sub += minutes+1;
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        }
                    }
                    else
                    {
                        long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                            sub += minutes+1;
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
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
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                            sub += (minutes+1)/60;
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        }
                    }
                    else
                    {
                        long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                        if(minutes>0)
                        {
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                            sub += (minutes+1)/60;
                            if(matcher2.find())
                                sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        }
                    }
                }
                else if(trimmed.matches("(\\[.*?])?rsvp .+(\\[.*?])?")) // rsvp count
                {
                    String name = trimmed.replaceAll("rsvp ","").replaceAll("\\[.*?]","");
                    List<String> members = entry.getRsvpMembers().get(name);
                    if(members != null)
                    {
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        sub += members.size();
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                    }
                }
                else if(trimmed.matches("(\\[.*?])?u(\\[.*?])?")) // advanced title url
                {
                    if(entry.getTitleUrl() != null)
                    {
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        sub += entry.getTitleUrl();
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                    }
                }
                else if(trimmed.matches("(\\[.*?])?v(\\[.*?])?")) // advanced image url
                {
                    if(entry.getImageUrl() != null)
                    {
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        sub += entry.getImageUrl();
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                    }
                }
                else if(trimmed.matches("(\\[.*?])?w(\\[.*?])?")) // advanced thumbnail url
                {
                    if(entry.getThumbnailUrl() != null)
                    {
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                        sub += entry.getThumbnailUrl();
                        if(matcher2.find())
                            sub += matcher2.group().replaceAll("[\\[\\]]", "");
                    }
                }
            }
            format = format.replace(group,sub);
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
                        if( i+1 < format.length() )
                        {
                            ch = format.charAt(i+1);
                            if( Character.isDigit( ch ) )
                            {
                                i++;
                                int x = Integer.parseInt("" + ch);
                                if(entry.getComments().size()>=x && x!=0)
                                {
                                    announceMsg.append(entry.getComments().get(x - 1));
                                }
                            }
                        }
                        break;
                    case 'f' :
                        for(String comment : entry.getComments())
                        {
                            announceMsg.append(comment).append("\n");
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
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hour(s)");
                                else
                                    announceMsg.append(" in ").append(minutes + 1).append(" minutes");
                            }
                        }
                        else
                        {
                            announceMsg.append("ends");
                            long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                            if(minutes>0)
                            {
                                if(minutes > 120)
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hour(s)");
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
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hour(s)");
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
                                    announceMsg.append(" in ").append((minutes + 1) / 60).append(" hour(s)");
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
                        announceMsg.append(entry.getStart().getDayOfMonth());
                        break;
                    case 'D' :
                        announceMsg.append(StringUtils.capitalize(entry.getStart().getDayOfWeek().toString()));
                        break;
                    case 'm' :
                        announceMsg.append(entry.getStart().getMonthValue());
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
     * Parses out the intended event repeat information from user input
     * @param input (String) the user input
     * @return (int) an integer representing the repeat information (stored in binary)
     */
    public static int parseRepeat(String input)
    {
        input = input.toLowerCase().trim();
        int bits = 0;
        if(input.toLowerCase().equals("daily"))
        {
            bits = 0b1111111;
        }
        else if(input.toLowerCase().equals("yearly"))
        {
            bits = 0b100000000;
        }
        else if(input.equals("off") || input.startsWith("no"))
        {
            bits = 0;
        }
        else
        {
            //String regex = "^((su(n(day)?)?)?(mo(n(day)?)?)?(tu(e(sday)?)?)?(we(d(nesday)?)?)?(th(u(rsday)?)?)?(fr(i(day)?)?)?(sa(t(urday)?)?)?)";
            String regex = "[,;:. ]([ ]+)?";
            String[] s = input.split(regex);
            for(String string : s)
            {
                if(string.matches("su(n(day)?)?")) bits |= 1;
                if(string.matches("mo(n(day)?)?")) bits |= 1<<1;
                if(string.matches("tu(e(sday)?)?")) bits |= 1<<2;
                if(string.matches("we(d(nesday)?)?")) bits |= 1<<3;
                if(string.matches("th(u(rsday)?)?")) bits |= 1<<4;
                if(string.matches("fr(i(day)?)?")) bits |= 1<<5;
                if(string.matches("sa(t(urday)?)?")) bits |= 1<<6;
            }
        }
        return bits;
    }


    /**
     * parses out repeat information for the 'interval' edit/create option
     * @param arg interval user-input
     * @return repeat bitset
     */
    public static int parseInterval(String arg)
    {
        int repeat = 0;
        if(arg.matches("\\d+([ ]?d(ay(s)?)?)?"))
            repeat = 0b10000000 | Integer.parseInt(arg.replaceAll("[^\\d]",""));
        else if(arg.matches("\\d+([ ]?m(in(utes)?)?)"))
            repeat = 0b100000000000 | Integer.parseInt(arg.replaceAll("[^\\d]",""));
        else if(arg.matches("\\d+([ ]?h(our(s)?)?)"))
            repeat = 0b100000000000 | (Integer.parseInt(arg.replaceAll("[^\\d]",""))*60);
        return repeat;
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
    public static LocalDate parseDate(String arg)
    {
        switch (arg)
        {
            case "today":
                return LocalDate.now();

            case "tomorrow":
                return LocalDate.now().plusDays(1);

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
                return date;
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
