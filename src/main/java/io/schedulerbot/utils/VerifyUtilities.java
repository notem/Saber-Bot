package io.schedulerbot.utils;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;

import java.time.Month;
import java.util.Arrays;
import java.util.List;

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

    public static boolean verifyScheduleChannel( Guild guild )
    {
        List<TextChannel> chans = guild.getTextChannelsByName( Main.getSettings().getScheduleChan(), false );
        if( chans == null || chans.isEmpty() )
        {
            return false;
        }

        Member botAsMember = null;
        String botId = Main.getBotSelfUser().getId();
        for(Member member : guild.getMembers())
        {
            if( member.getUser().getId().equals(botId) )
                botAsMember = member;
        }
        if( botAsMember == null )
        {
            // should get here, but just in case it were null. . .
            return false;
        }

        List<Permission> perms = Arrays.asList(
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_READ,
                Permission.MESSAGE_WRITE, Permission.MESSAGE_MANAGE
        );
        return botAsMember.hasPermission( chans.get(0), perms );
    }
}
