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
    private String prefix = Main.getBotSettingsManager().getCommandPrefix();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``!edit <ID> <option> <arg>`` will allow you to change an" +
                " entry's title, start time, start date, end time, comments," +
                " and repeat parameters.\n\n" +
                " The possible arguments are ``title <title>``, ``start <h:mm>``, ``end <h:mm>``, ``date MM/dd``, " +
                "``repeat <no|daily|Su,Mo,Tu. . .>``, and ``comment add|remove <comment>``. When " +
                "removing a comment, either the comment copied verbatim or the comment number needs to be supplied.";

        String EXAMPLES = "" +
                "Ex1: ``!edit 3fa0 comment add \"Attendance is mandatory\"``" +
                "\nEx2: ``!edit 0abf start 21:15``" +
                "\nEx3: ``!edit 49af end 2:15pm``" +
                "\nEx4: ``!edit 80c0 comment remove 1``";

        String USAGE_BRIEF = "``" + prefix + "edit`` - modify an event";

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
            return "That's not enough arguments!";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "``" + args[index] + "`` is not a valid entry ID!";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
            return "I could not find an entry with that ID!";

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
                            return "I cannot use **" + args[index] + "** to remove a comment!";
                        break;
                    default:
                        return "The only valid options for ``comment`` is **add** or **remove***!";
                }
                break;

            case "starts":
            case "start":
                if(args.length > 3)
                    return "That's too many arguments for **start**!";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                if( entry.hasStarted() )
                    return "You cannot modify the start time after the event has already started.";
                break;

            case "ends":
            case "end":
                if(args.length > 3)
                    return "That's too many arguments for **end**!";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                break;

            case "title":
                if( args[index].length() > 255 )
                    return "Your title can be at most 255 characters!";
                break;

            case "date":
                if(args.length > 3)
                    return "That's too many arguments for **date**!";
                if( !VerifyUtilities.verifyDate( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                if(entry.hasStarted())
                    return "You cannot modify the date of events which have already started!";
                break;

            case "repeats":
            case "repeat":
                if(args.length > 3)
                    return "That's too many arguments for **repeat**!";
                break;

            case "url":
                if (args.length > 3)
                    return "That's too many arguments for **repeat**!";
                if (!VerifyUtilities.verifyUrl(args[index]))
                    return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                break;

            default:
                return "**" + args[index] + "** is not an option I know of! Please use ``" +
                        prefix + "help edit`` to see available options!";
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

            case "title":
                title = args[index];
                break;

            case "date":
                LocalDate date = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                start = start.withMonth(date.getMonthValue())
                        .withDayOfMonth(date.getDayOfMonth());
                end = end.withMonth(date.getMonthValue())
                        .withDayOfMonth(date.getDayOfMonth());

                if(end.isBefore(start))
                    end.plusDays(1);
                if(Instant.now().isAfter(start.toInstant()))
                    start.plusYears(1);
                if(Instant.now().isAfter(end.toInstant()))
                    start.plusYears(1);

                break;

            case "repeats":
            case "repeat":
                String tmp = args[index].toLowerCase();
                repeat = ParsingUtilities.parseWeeklyRepeat(tmp);
                break;

            case "url":
                url = args[index];
                break;

        }

        Main.getEntryManager().updateEntry(entryId, title, start, end, comments, repeat, url, msg);
    }
}
