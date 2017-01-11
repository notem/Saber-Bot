package ws.nmathe.saber.utils;

import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 */
public class ParsingUtilities
{
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
                                if(entry.eComments.size()>=x && x!='0')
                                {
                                    announceMsg += entry.eComments.get(x-1);
                                }
                            }
                        }
                        break;

                    case 'a' :
                        if( entry.eStart.equals(entry.eEnd) )
                            break;

                        if( !entry.startFlag )
                            announceMsg += "begins";

                        else
                            announceMsg += "ends";

                        break;

                    case 't' :
                        announceMsg += entry.eTitle;
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
