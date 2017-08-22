package ws.nmathe.saber.utils;

import java.net.URL;
import java.time.LocalDate;

/**
 * static methods used to verify user input for the verify() method of commands
 */
public class VerifyUtilities
{
    public static boolean verifyTime(String arg)
    {
        if( arg.toUpperCase().endsWith("AM") || arg.toUpperCase().endsWith("PM") )
        {
            String[] start = arg.substring(0,arg.length()-2).split(":");
            if (start.length != 2)
                return false;
            if (start[0].length() > 2)
                return false;
            if( !verifyInteger( start[0] ) )
                return false;
            if (Integer.parseInt(start[0]) > 12 || Integer.parseInt(start[0]) == 0)
                return false;
            if (start[1].length() > 2)
                return false;
            if( !verifyInteger( start[1] ) )
                return false;
            if (Integer.parseInt(start[1]) > 59)
                return false;

        }
        else
        {
            String[] start = arg.split(":");
            if (start.length != 2)
                return false;
            if (start[0].length() > 2)
                return false;
            if( !verifyInteger( start[0] ) )
                return false;
            if (Integer.parseInt(start[0]) > 24)
                return false;
            if (start[1].length() > 2)
                return false;
            if( !verifyInteger( start[1] ) )
                return false;
            if (Integer.parseInt(start[1]) > 59)
                return false;
            if (Integer.parseInt(start[0]) == 24 && Integer.parseInt(start[1]) != 0)
                return false;
        }
        return true;
    }

    public static boolean verifyDate( String arg )
    {
        if(arg.toLowerCase().equals("tomorrow") || arg.toLowerCase().equals("today"))
            return true;

        try
        {
            String[] splt = arg.split("[^0-9]+");
            if(splt.length == 1)
            {
                LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), Integer.parseInt(splt[0]));
            }
            else if(splt.length == 2)
            {
                LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(splt[0]), Integer.parseInt(splt[1]));
            }
            else if(splt.length == 3)
            {
                LocalDate.of(Integer.parseInt(splt[0]), Integer.parseInt(splt[1]), Integer.parseInt(splt[2]));
            }
            else
            {
                return false;
            }
            return true;
        }
        catch(Exception e)
        {
            return false;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean verifyHex(String arg)
    {
        try
        {
            Integer.decode("0x"+arg);
        }
        catch(Exception e)
        {
            return false;
        }

        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean verifyInteger(String arg)
    {
        try
        {
            Integer.parseInt(arg);
        }
        catch (Exception e)
        {
            return false;
        }
        return true;
    }

    public static boolean verifyUrl(String arg)
    {
        try
        {
            (new URL(arg)).getContent();
        }
        catch(Exception e)
        {
            return false;
        }
        return true;
    }
}
