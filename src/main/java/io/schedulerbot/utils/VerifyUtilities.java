package io.schedulerbot.utils;

import java.time.Month;

/**
 * static methods frequently used in the verify() methods used by classes implementing the
 * Command interface.
 */
public class VerifyUtilities
{
    public static boolean verifyTime(String arg)
    {
        String[] start = arg.split(":");
        if(start.length != 2)
            return false;
        if(start[0].length()!=2)
            return false;
        else
        {
            if(!Character.isDigit(start[0].charAt(0)))
                return false;
            if(!Character.isDigit(start[0].charAt(1)))
                return false;
            if(Integer.parseInt(start[0])>24)
                return false;
        }
        if(start[1].length()!=2)
            return false;
        else
        {
            if(!Character.isDigit(start[1].charAt(0)))
                return false;
            if(!Character.isDigit(start[1].charAt(1)))
                return false;
            if(Integer.parseInt(start[1])>59)
                return false;
            if(Integer.parseInt(start[0])==24&&Integer.parseInt(start[1])!=0)
                return false;
        }
        return true;
    }

    public static boolean verifyDate( String arg )
    {
        if( arg.toLowerCase().equals("tomorrow") || arg.toLowerCase().equals("today") )
            return true;

        String[] date = arg.split("/");
        if(date.length != 2)
            return false;
        if(date[0].length()!=2)
            return false;
        else
        {
            if(!Character.isDigit(date[0].charAt(0)))
                return false;
            if(!Character.isDigit(date[0].charAt(1)))
                return false;
            if(Integer.parseInt(date[0])>12||Integer.parseInt(date[0])==0)
                return false;
        }
        if(date[1].length()!=2)
            return false;
        else
        {
            if(!Character.isDigit(date[1].charAt(0)))
                return false;
            if(!Character.isDigit(date[1].charAt(1)))
                return false;
            if(Integer.parseInt(date[1])>Month.of(Integer.parseInt(date[0])).minLength()
                    ||Integer.parseInt(date[0])==0)
                return false;
        }

        return true;
    }

    public static boolean verifyHex( String arg )
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

    public static boolean verifyString( String[] args )
    {
        if(!args[0].startsWith("\""))
            return false;
        if(!args[args.length-1].endsWith("\""))
            return false;

        return true;
    }

    public static boolean verifyRepeat( String arg )
    {
        String argLower = arg.toLowerCase();
        if(argLower.equals("no")||argLower.equals("weekly")||argLower.equals("daily"))
            return true;

        return false;
    }

    public static boolean verifyInteger( String arg )
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
}
