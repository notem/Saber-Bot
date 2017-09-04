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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * utilities which do some sort of string parsing
 */
public class ParsingUtilities
{
    /**
     * parses a local time string inputted by a user into a ZonedDateTime object
     * @param t a dummy ZonedDateTime with the desired zone and date set
     * @param localtime the local time
     * @return new ZonedDateTime with the new time
     */
    public static ZonedDateTime parseTime(ZonedDateTime t, String localtime)
    {
        LocalTime time;

        if( localtime.equals("24:00") )       // if the user inputs 24:00, convert internally to 0:00
            time = LocalTime.MAX;
        else
        {
            if( localtime.toUpperCase().endsWith("AM") || localtime.toUpperCase().endsWith("PM") )
                time = LocalTime.parse(localtime.toUpperCase(), DateTimeFormatter.ofPattern("h:mma"));
            else
                time = LocalTime.parse(localtime.toUpperCase(), DateTimeFormatter.ofPattern("H:mm"));
        }

        t = t.withHour(time.getHour());
        t = t.withMinute(time.getMinute());
        return t;
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
     * @param str (String) the user input
     * @return (int) an integer representing the repeat information (stored in binary)
     */
    public static int parseWeeklyRepeat(String str)
    {
        str = str.toLowerCase();
        int bits = 0;
        if( str.toLowerCase().equals("daily") )
        {
            bits = 0b1111111;
        }
        else if( str.equals("off") || str.startsWith("no") )
        {
            bits = 0;
        }
        else
        {
            // TODO better parsing
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
        }
        return bits;
    }


    /**
     * Parses user supplied input for information indicating the reminder intervals to use for a schedule's
     * reminder settings
     * @param arg (String) user input
     * @return (List<Integer>) a list of integers representing the time (in minutes) before an event starts
     */
    public static List<Integer> parseReminderStr(String arg)
    {
        List<Integer> list = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(arg);
        while (matcher.find())
        {
            String group = matcher.group();
            if(VerifyUtilities.verifyInteger(group))
            {
                list.add(Integer.parseInt(group));
            }
        }
        return list;
    }


    /**
     * Parses user input for a date information
     * @param arg (String)
     * @return
     */
    public static LocalDate parseDateStr(String arg)
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
