package ws.nmathe.saber.utils;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
            LocalDate.parse(arg, DateTimeFormatter.ofPattern("MM d"));
            return true;
        }
        catch(DateTimeParseException e)
        {
            return false;
        }
    }

    public static boolean verifyHex(String arg)
    {
        try
        {
            Integer.decode("0x"+arg);
        }
        catch( Exception e )
        {
            return false;
        }

        return true;
    }

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
            new URL(arg);
        }
        catch(Exception e)
        {
            return false;
        }
        return true;
    }
}
