package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.schedule.ScheduleEntryParser;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import ws.nmathe.saber.utils.__out;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;

/**
 */
public class EditCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private ScheduleManager schedManager = Main.getScheduleManager();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "The entry's title, start time, start date, end time, comments," +
                " and repeat may be reconfigured with this command using the form **!edit <ID> <option> <arg>**\n\n" +
                " The possible arguments are **title <title>**, **start h:mm**, **end h:mm**, **date MM/dd**, " +
                "**repeat no**/**daily**/**weekly**, and **comment add <comment>** (or **comment remove**). When " +
                "removing a comment, either the comment copied verbatim or the comment number needs to be supplied.";

        String EXAMPLES = "Ex1: **!edit 3fa0 comment add \"Attendance is mandatory\"**" +
                "\nEx2: **!edit 0abf start 21:15**" +
                "\nEx3: **!edit 49af end 2:15pm**" +
                "\nEx4: **!edit 80c0 comment remove 1**";

        String USAGE_BRIEF = "``" + prefix + "edit`` - Modifies an schedule entry, either" +
                " changing parameters or adding/removing comment fields.";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if( args.length < 3 )
            return "Not enough arguments";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "ID \"" + args[index] + "\" is not a valid ID value";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = schedManager.getEntry( Id );

        index++;

        // check later args
        switch( args[index++].toLowerCase() )
        {
            case "comment":
                switch (args[index++])
                {
                    case "add":
                        break;
                    case "remove":
                        if(Character.isDigit(args[index].charAt(0)) &&
                                !VerifyUtilities.verifyInteger(args[index]))
                            return "Argument **" + args[index] + "** cannot be used to remove a comment";
                        break;
                    default:
                        return "Argument **" + args[index] + "** is not a valid option for **comment**";
                }
                break;

            case "start":
                if(args.length > 3)
                    return "Not enough arguments";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "Argument **" + args[index] + "** is not a valid start time";
                if( entry.hasStarted() )
                    return "You cannot modify the start time after the event has already started.";
                break;

            case "end":
                if(args.length > 3)
                    return "Not enough arguments";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "Argument **" + args[index] + "** is not a valid end time";
                break;

            case "title":
                if( args[index].length() > 255 )
                    return "Your title is too long";
                break;

            case "date":
                if(args.length > 3)
                    return "Not enough arguments";
                if( !VerifyUtilities.verifyDate( args[index] ) )
                    return "Argument **" + args[index] + "** is not a valid date";
                break;

            case "repeat":
                if(args.length > 3)
                    return "Not enough arguments";
                break;
        }

        return ""; // return valid
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        Integer entryId = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = schedManager.getEntry( entryId );

        String title = entry.getTitle();                    //
        ArrayList<String> comments = entry.getComments();   // initialize using old
        ZonedDateTime start = entry.getStart();             // schedule values
        ZonedDateTime end = entry.getEnd();                 //
        int repeat = entry.getRepeat();                     //

        index++;    // 1

        switch( args[index++] )     // 2
        {
            case "comment":
                switch( args[index++] )   // 3
                {
                    case "add" :
                        comments.add( args[index] );
                        break;
                    case "remove" :
                        if( VerifyUtilities.verifyInteger(args[index]) )
                        {
                            comments.remove( Integer.parseInt(args[index])-1 );
                        }
                        else
                            comments.remove(args[index]);
                        break;
                }
                break;

            case "start":
                start = ParsingUtilities.parseTime( start, args[index] );

                if(ZonedDateTime.now().isAfter(start)) //add a day if the time has already passed
                {
                    start = start.plusDays(1);
                }
                if(start.isAfter(end))        //add a day to end if end is after start
                {
                    end = end.plusDays(1);
                }
                break;

            case "end":
                end = ParsingUtilities.parseTime( end, args[index] );

                if(ZonedDateTime.now().isAfter(end)) //add a day if the time has already passed
                {
                    end = end.plusDays(1);
                }
                if(start.isAfter(end))        //add a day to end if end is after start
                {
                    end = end.plusDays(1);
                }
                break;

            case "title":
                title = args[index];
                break;

            case "date":
                start = start.withMonth(Integer.parseInt(args[index].split("/")[0]))
                        .withDayOfMonth(Integer.parseInt(args[index].split("/")[1]));
                end = end.withMonth(Integer.parseInt(args[index].split("/")[0]))
                        .withDayOfMonth(Integer.parseInt(args[index].split("/")[1]));
                if( end.isBefore(start) )
                {
                    end.plusDays(1);
                }
                break;

            case "repeat":
                String tmp = args[index].toLowerCase();
                if( tmp.toLowerCase().equals("daily") )
                    repeat = 0b1111111;
                else if( tmp.equals("no") || tmp.equals("none") )
                    repeat = 0;
                else
                {
                    repeat = 0;
                    if( tmp.contains("su") )
                        repeat |= 1;
                    if( tmp.contains("mo") )
                        repeat |= 1<<1;
                    if( tmp.contains("tu") )
                        repeat |= 1<<2;
                    if( tmp.contains("we") )
                        repeat |= 1<<3;
                    if( tmp.contains("th") )
                        repeat |= 1<<4;
                    if( tmp.contains("fr") )
                        repeat |= 1<<5;
                    if( tmp.contains("sa") )
                        repeat |= 1<<6;
                }
                break;
        }


        Message msg;
        synchronized( schedManager.getScheduleLock() )
        {
            msg = ScheduleEntryParser.generate(title, start, end, comments, repeat, entryId, entry.getMessage().getChannel().getId());
            schedManager.removeEntry( entryId );    // remove the old entry
        }

        int finalRepeat = repeat;           //
        ZonedDateTime finalEnd = end;       // convert into effectively final
        ZonedDateTime finalStart = start;   // variables
        String finalTitle = title;          //

        MessageUtilities.editMsg(msg,
                entry.getMessage(),
                (message)->schedManager.addEntry(finalTitle, finalStart, finalEnd, comments, entryId, message, finalRepeat ));
    }
}
