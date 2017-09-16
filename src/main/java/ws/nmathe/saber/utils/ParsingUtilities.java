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
    public static String parseMsgFormat(String format, ScheduleEntry entry)
    {
        String announceMsg = "";
        for( int i = 0; i < format.length(); i++ )
        {
            char ch = format.charAt(i);
            if( ch == '%' && i+1 < format.length() )
            {
                i++;
                ch = format.charAt(i);

                switch( ch )
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
                                    announceMsg += entry.getComments().get(x-1);
                                }
                            }
                        }
                        break;

                    case 'f' :
                        for(String comment : entry.getComments())
                        {
                            announceMsg += comment + "\n";
                        }

                    case 'a' :
                        if( !entry.hasStarted() )
                        {
                            announceMsg += "begins";
                            if(!entry.getReminders().isEmpty())
                            {
                                long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES)+1;
                                if(minutes > 120)
                                    announceMsg += " in " + minutes/60 + " hours";
                                else
                                    announceMsg += " in " + minutes + " minutes";
                            }
                        }
                        else
                        {
                            announceMsg += "ends";
                        }
                        break;

                    case 'b' :
                        if( !entry.hasStarted() )
                        {
                            announceMsg += "begins";
                        }
                        else
                        {
                            announceMsg += "ends";
                        }
                        break;

                    case 'x' :
                        if( !entry.hasStarted() )
                        {
                            if(!entry.getReminders().isEmpty())
                            {
                                long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES)+1;
                                if(minutes > 120)
                                    announceMsg += "in " + minutes/60 + " hours";
                                else
                                    announceMsg += "in " + minutes + " minutes";
                            }
                        }
                        break;

                    case 't' :
                        announceMsg += entry.getTitle();
                        break;

                    case 'd' :
                        announceMsg += entry.getStart().getDayOfMonth();
                        break;

                    case 'D' :
                        announceMsg += StringUtils.capitalize(entry.getStart().getDayOfWeek().toString());
                        break;

                    case 'm' :
                        announceMsg += entry.getStart().getMonthValue();
                        break;

                    case 'M' :
                        announceMsg += StringUtils.capitalize(entry.getStart().getMonth().toString());
                        break;

                    case 'y' :
                        announceMsg += entry.getStart().getYear();
                        break;

                    case 'i':
                        announceMsg += Integer.toHexString(entry.getId());
                        break;

                    case '%' :
                        announceMsg += '%';
                        break;

                    case 'u' :
                        announceMsg += entry.getTitleUrl()==null?"":entry.getTitleUrl();
                        break;

                    case 'v' :
                        announceMsg += entry.getImageUrl()==null?"":entry.getImageUrl();
                        break;

                    case 'w':
                        announceMsg += entry.getThumbnailUrl()==null?"":entry.getThumbnailUrl();
                        break;
                }

            }
            else
            {
                announceMsg += ch;
            }
        }

        return announceMsg;
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
     * Parses user supplied input for information indicating the reminder intervals to use for a schedule's
     * reminder settings
     * @param arg (String) user input
     * @return (Set<Integer>) a linked set of integers representing the time (in minutes) before an event starts
     */
    public static Set<Integer> parseReminder(String arg)
    {
        Set<Integer> list = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\d+[^\\d]?").matcher(arg);
        while(matcher.find())
        {
            String group = matcher.group();
            Logging.info(ParsingUtilities.class, group);

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
     * @return
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
}
