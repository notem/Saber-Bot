package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.ScheduleEntry;
import io.schedulerbot.core.ScheduleManager;
import io.schedulerbot.core.ScheduleEntryParser;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;

/**
 */
public class EditCommand implements Command
{
    private static String prefix = Main.getSettings().getCommandPrefix();

    private static final String USAGE_EXTENDED = "\nThe entry's title, start time, start date, end time, comments," +
            " and repeat may be reconfigured with this command using the form **!edit <ID> <option> <arguments>**\n The" +
            " possible arguments are **title \"NEW TITLE\"**, **start HH:mm**, **end HH:mm**, **date MM/dd**, " +
            "**repeat no**/**daily**/**weekly**, and **comment add \"COMMENT\"** (or **comment remove**). When " +
            "removing a comment, either the comment copied verbatim (within quotations) or the comment number needs" +
            " to be supplied.\n\nEx1: **!edit 3fa0 comment add \"Attendance is mandatory\"**\nEx2: **!edit 0abf " +
            "start 06:15**\nEx3: **!edit 80c0 comment remove 1**";

    private static final String USAGE_BRIEF = "**" + prefix + "edit** - Modifies an event entry, either" +
            " changing settings or adding/removing comment fields.";

    private ScheduleManager scheduleManager = Main.scheduleManager;

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }


    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        if( args.length < 3 )
            return false;

        // check first arg
        if( !VerifyUtilities.verifyHex(args[0]) )
            return false;

        // check later args
        switch( args[1].toLowerCase() )
        {
            case "comment":
                switch (args[2])
                {
                    case "add":
                        if (!VerifyUtilities.verifyString(Arrays.copyOfRange(args, 3, args.length)))
                            return false;
                        break;
                    case "remove":
                        if(Character.isDigit(args[3].charAt(0)) &&
                                !VerifyUtilities.verifyInteger(args[3]))
                            return false;
                        else
                        {
                            if (!VerifyUtilities.verifyString(Arrays.copyOfRange(args, 3, args.length)))
                                return false;
                        }
                        break;
                    default:
                        return false;
                }
                break;

            case "start":
                if(args.length > 3)
                    return false;
                if( !VerifyUtilities.verifyTime( args[2] ) )
                    return false;
                break;

            case "end":
                if(args.length > 3)
                    return false;
                if( !VerifyUtilities.verifyTime( args[2] ) )
                    return false;
                break;

            case "title":
                String[] comment = Arrays.copyOfRange( args, 2, args.length );
                if(!VerifyUtilities.verifyString(comment))
                    return false;
                break;

            case "date":
                if(args.length > 3)
                    return false;
                if( !VerifyUtilities.verifyDate( args[2] ) )
                    return false;
                break;

            case "repeat":
                if(args.length > 3)
                    return false;
                if( !VerifyUtilities.verifyRepeat(args[2]) )
                    return false;
                break;
        }

        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // parse argument into the event entry's ID
        Integer entryId = Integer.decode( "0x" + args[0] );

        // check if the entry exists
        ScheduleEntry entry = scheduleManager.getEntry( entryId );
        if( entry == null  || !entry.eMsg.getGuild().getId().equals(event.getGuild().getId()) )
        {
            String msg = "There is no event entry with ID " + Integer.toHexString(entryId) + ".";
            event.getChannel().sendMessage( msg ).queue();
            return;
        }

        String title = entry.eTitle;
        ArrayList<String> comments = entry.eComments;
        ZonedDateTime start = entry.eStart;
        ZonedDateTime end = entry.eEnd;
        int repeat = entry.eRepeat;
        LocalDate date = LocalDate.now();

        switch( args[1] )
        {
            case "comment":
                if( args[2].equals("add") )
                {
                    String comment = "";
                    for( int i = 3; i < args.length; i++ )
                    {
                        comment += args[i].replace("\"", "");
                        if (!(i == args.length - 1))
                            comment += " ";
                    }
                    comments.add( comment );
                }
                else if( args[2].equals("remove"))
                {
                    if( args[3].charAt(0)=='\"' )
                    {
                        String comment = "";
                        for (int i = 3; i < args.length; i++) {
                            comment += args[i].replace("\"", "");
                            if (!(i == args.length - 1))
                                comment += " ";
                        }
                        comments.remove(comment);
                    }
                    else if( Integer.parseInt(args[3]) <= comments.size() )
                        comments.remove( Integer.parseInt(args[3])-1 );
                }
                break;
/**
            case "start":
                if( args[2].equals("24:00") )       // if the user inputs 24:00
                    start = LocalTime.MIDNIGHT;     // convert to 00:00
                else
                    start = LocalTime.parse(args[2]);
                break;

            case "end":
                if( args[2].equals("24:00") )       // if the user inputs 24:00
                    end = LocalTime.MIDNIGHT;       // convert to 00:00
                else
                    end = LocalTime.parse(args[2]);
                break;
*/
            case "title":
                title = "";
                for( int i = 2; i < args.length; i++ )
                {
                    title += args[i].replace("\"", "");
                    if(!(i == args.length-1))
                        title += " ";
                }
                break;

            case "date":
                if( args[2].toLowerCase().equals("today") )
                        date = LocalDate.now();
                    else if( args[2].toLowerCase().equals("tomorrow") )
                        date = LocalDate.now().plusDays( 1 );
                    else if( Character.isDigit(args[2].charAt(0)) )
                    {
                        date = date.withMonth(Integer.parseInt(args[2].split("/")[0]));
                        date = date.withDayOfMonth(Integer.parseInt(args[2].split("/")[1]));
                    }
                break;

            case "repeat":
                if( Character.isDigit( args[2].charAt(0) ))
                    repeat = Integer.parseInt(args[2]);
                else if( args[2].equals("daily") )
                    repeat = 1;
                else if( args[2].equals("weekly") )
                    repeat = 2;
                else if( args[2].equals("no") )
                    repeat = 0;
                break;
        }

        scheduleManager.removeEntry(entryId);

        String msg = ScheduleEntryParser.generate(title, start, end, comments, repeat, entryId);
        MessageUtilities.editMsg(msg, entry.eMsg, (m) -> scheduleManager.addEntry(m));
    }
}
