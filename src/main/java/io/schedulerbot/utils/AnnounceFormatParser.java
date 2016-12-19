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
            if( ch == '%' && i+1 != format.length() )
            {
                __out.printOut(AnnounceFormatParser.class, announceMsg);
                i++;
                ch = format.charAt(i);
                switch( ch )
                {
                    case 'c' :
                        if( i+1 != format.length() )
                        {
                            ch = format.charAt(i+1);
                            if( Character.isDigit( ch ) )
                            {
                                i++;
                                announceMsg += entry.eComments.get(Integer.parseInt("" + ch)+1);
                            }
                        }
                        break;

                    case 'a' :
                        if( !entry.startFlag )
                        {
                            announceMsg += "begun";
                        }
                        else
                        {
                            announceMsg += "ended";
                        }
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
