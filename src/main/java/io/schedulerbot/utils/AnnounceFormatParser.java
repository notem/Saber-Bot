package io.schedulerbot.utils;

import io.schedulerbot.core.ScheduleEntry;


/**
 */
public class AnnounceFormatParser
{
    public static String parse(String format, ScheduleEntry entry)
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
