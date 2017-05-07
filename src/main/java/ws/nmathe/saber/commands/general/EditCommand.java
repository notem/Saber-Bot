package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;

/**
 */
public class EditCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "edit";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <ID> <option> <arg>```\n" +
                "The edit command will allow you to change an event's settings." +
                "\n\n" +
                "``<option>`` is to contain which attribute of the event you wish to edit. ``<arg>`` should be the" +
                " new configuration." +
                "\n\n```diff\n+ Options ```\n" +
                "List of ``<option>``s: ``start``, ``end``, ``title``, ``comment <add|remove>``, ``date``, " +
                "``start-date``, ``end-date``, ``repeat``, ``interval``, and ``url``.\n\n" +
                "For further explanation as for what are valid arguments for each options, reference the ``help`` information" +
                " for the ``create`` command.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " 3fa0dd0 comment add \"Attendance is mandatory\"``" +
                "\n``" + invoke + " 0abf2991 start 21:15``" +
                "\n``" + invoke + " 49afaf2 end 2:15pm``" +
                "\n``" + invoke + " 409fa22 start-date 10/9``" +
                "\n``" + invoke + " a00af9a repeat \"Su, Tu, Fr\"``" +
                "\n``" + invoke + " 80c0sd09 comment remove 1``";

        String USAGE_BRIEF = "``" + invoke + "`` - modify an event";

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
            return "That's not enough arguments! Use ``" + invoke + " <ID> <option> <arg>``";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "``" + args[index] + "`` is not a valid entry ID!";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
            return "I could not find an entry with that ID!";

        if(Main.getScheduleManager().isLocked(entry.getScheduleID()))
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes.";

        index++;

        // check later args
        switch( args[index++].toLowerCase() )
        {
            case "c":
            case "comment":
                switch (args[index++])
                {
                    case "a":
                    case "add":
                        break;
                    case "r":
                    case "remove":
                        if(Character.isDigit(args[index].charAt(0)) &&
                                !VerifyUtilities.verifyInteger(args[index]))
                            return "I cannot use **" + args[index] + "** to remove a comment!";
                        break;
                    default:
                        return "The only valid options for ``comment`` is **add** or **remove***!";
                }
                break;

            case "s":
            case "starts":
            case "start":
                if(args.length > 3)
                    return "That's too many arguments for **start**!";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                if( entry.hasStarted() )
                    return "You cannot modify the start time after the event has already started.";
                break;

            case "e":
            case "ends":
            case "end":
                if(args.length > 3)
                    return "That's too many arguments for **end**!";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                break;

            case "t":
            case "title":
                if( args[index].length() > 255 )
                    return "Your title can be at most 255 characters!";
                break;

            case "d":
            case "date":
            case "sd":
            case "start-date":
            case "ed":
            case "end-date":
                if(args.length > 3)
                    return "That's too many arguments for **date**!";
                if( !VerifyUtilities.verifyDate( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                if(ParsingUtilities.parseDateStr(args[index]).isBefore(LocalDate.now()))
                    return "That date is in the past!";
                if(entry.hasStarted())
                    return "You cannot modify the date of events which have already started!";
                break;

            case "r":
            case "repeats":
            case "repeat":
                if(args.length > 3)
                    return "That's too many arguments for **repeat**!";
                break;

            case "i":
            case "interval":
                if(args.length > 3)
                    return "That's too many arguments for **interval**!";
                if(!VerifyUtilities.verifyInteger(args[index]))
                    return "**" + args[index] + "** is not a number!";
                if(Integer.parseInt(args[index]) < 1)
                    return "Your repeat interval can't be negative!";
                break;

            case "u":
            case "url":
                if (args.length > 3)
                    return "That's too many arguments for **repeat**!";
                if (!VerifyUtilities.verifyUrl(args[index]))
                    return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                break;

            default:
                return "**" + args[index] + "** is not an option I know of! Please use the ``help`` command to see available options!";
        }

        return ""; // return valid
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        Integer entryId = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

        Message msg = entry.getMessageObject();
        if( msg==null )
            return;

        String title = entry.getTitle();                    //
        ArrayList<String> comments = entry.getComments();   // initialize using old
        ZonedDateTime start = entry.getStart();             // schedule values
        ZonedDateTime end = entry.getEnd();                 //
        int repeat = entry.getRepeat();                     //
        String url = entry.getTitleUrl();                   //

        index++;    // 1

        switch( args[index++] )     // 2
        {
            case "c":
            case "comment":
                switch( args[index++] )   // 3
                {
                    case "a":
                    case "add" :
                        comments.add( args[index] );
                        break;
                    case "r":
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

            case "s":
            case "starts":
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

            case "e":
            case "ends":
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

            case "t":
            case "title":
                title = args[index];
                break;

            case "d":
            case "date":
                LocalDate date = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                start = start.withMonth(date.getMonthValue())
                        .withDayOfMonth(date.getDayOfMonth());
                end = end.withMonth(date.getMonthValue())
                        .withDayOfMonth(date.getDayOfMonth());
                break;

            case "sd":
            case "start date":
            case "start-date":
                LocalDate sdate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                start = start.withMonth(sdate.getMonthValue())
                        .withDayOfMonth(sdate.getDayOfMonth());

                if(end.isBefore(start))
                    end = start.plusDays(1);
                break;

            case "ed":
            case "end date":
            case "end-date":
                LocalDate edate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                end = end.withMonth(edate.getMonthValue())
                        .withDayOfMonth(edate.getDayOfMonth());
                break;

            case "r":
            case "repeats":
            case "repeat":
                String tmp = args[index].toLowerCase();
                repeat = ParsingUtilities.parseWeeklyRepeat(tmp);
                break;

            case "i":
            case "interval":
                repeat = 0b10000000 | Integer.parseInt(args[index]);
                break;

            case "u":
            case "url":
                url = args[index];
                break;

        }

        Main.getEntryManager().updateEntry(entryId, title, start, end, comments,
                repeat, url, entry.hasStarted(), msg, entry.getGoogleId(), entry.getRsvpYes(), entry.getRsvpNo());
    }
}
