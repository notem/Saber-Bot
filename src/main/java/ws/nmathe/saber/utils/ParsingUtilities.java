package ws.nmathe.saber.utils;

import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
                                int x = Integer.parseInt("" + ch);
                                i++;
                                if(entry.getComments().size()>=x && x!='0')
                                {
                                    announceMsg += entry.getComments().get(x-1);
                                }
                            }
                        }
                        break;

                    case 'a' :
                        if( entry.getStart().equals(entry.getEnd()) )
                            break;

                        if( !entry.hasStarted() )
                            announceMsg += "begins";

                        else
                            announceMsg += "ends";

                        break;

                    case 't' :
                        announceMsg += entry.getTitle();
                        break;

                    case '%' :
                        announceMsg += '%';
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
}
