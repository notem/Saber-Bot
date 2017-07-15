package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.MessageGenerator;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
                "``start-date``, ``end-date``, ``repeat``, ``interval``, ``url``, ``quiet-start``, ``quiet-end``, ``quiet-remind``, ``expire``" +
                " and ``max``.\n\n" +
                "Most of the options listed above accept the same arguments as the ``create`` command." +
                "\nReference the ``help`` information for the ``create`` command for more information.\n\n" +
                "Announcements for individual events can be toggled on-off using any of these three options: " +
                "``quiet-start``, ``quiet-end``, ``quiet-remind``\n" +
                "No additional arguments need to be provided when using one of the ``quiet-`` options.\n\n" +
                "If the schedule that the event is placed on is rsvp enabled (which may be turned on using the ``config`` command)" +
                " a limit to the number of users who may rsvp 'yes' can be set using the ``max`` option.\n" +
                "The ``max`` option requires one additional argument, which is the maximum number of players allowed to rsvp for the event.\n" +
                "Use \"off\" as the argument to remove a previously set limit.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " 3fa0dd0 comment add \"Attendance is mandatory\"``" +
                "\n``" + invoke + " 0abf2991 start 21:15``" +
                "\n``" + invoke + " 49afaf2 end 2:15pm``" +
                "\n``" + invoke + " 409fa22 start-date 10/9``" +
                "\n``" + invoke + " a00af9a repeat \"Sun, Tue, Fri\"``" +
                "\n``" + invoke + " 80c0sd09 comment remove 1``" +
                "\n``" + invoke + " 0912af9 quiet-start``" +
                "\n``" + invoke + " a901992 expire 2019/1/1";

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

        if( args.length < 1 )
            return "That's not enough arguments! Use ``" + invoke + " <ID> [<option> <arg>]``";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "``" + args[index] + "`` is not a valid entry ID!";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
            return "I could not find an entry with that ID!";

        if(Main.getScheduleManager().isLocked(entry.getScheduleID()))
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes.";

        if(args.length == 1)
            return "";

        index++; // 1

        // check later args
        switch( args[index++].toLowerCase() ) // 2
        {
            case "c":
            case "comment":
                if(args.length <= index+1)
                    return "That's not enough arguments for *comment*! Use ``"+ invoke +" [id] comment [add|remove] \"comment\"``";

                switch (args[index++]) // 3
                {
                    case "a":
                    case "add":
                        break;
                    case "r":
                    case "remove":
                        if((!args[index].isEmpty() && Character.isDigit(args[index].charAt(0)))
                                && !VerifyUtilities.verifyInteger(args[index]))
                        {
                            return "I cannot use **" + args[index] + "** to remove a comment!";
                        }
                        break;
                    default:
                        return "The only valid options for ``comment`` is **add** or **remove***!";
                }
                break;

            case "s":
            case "starts":
            case "start":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [start time]``";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                if( entry.hasStarted() )
                    return "You cannot modify the start time after the event has already started.";
                break;

            case "e":
            case "ends":
            case "end":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [end time]``";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                break;

            case "t":
            case "title":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [\"title\"]``";
                if( args[index].length() > 255 )
                    return "Your title can be at most 255 characters!";
                break;

            case "d":
            case "date":
            case "sd":
            case "start-date":
            case "ed":
            case "end-date":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [date]``";
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
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [repeat]``";
                break;

            case "i":
            case "interval":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [number]``";
                if(!VerifyUtilities.verifyInteger(args[index]))
                    return "**" + args[index] + "** is not a number!";
                if(Integer.parseInt(args[index]) < 1)
                    return "Your repeat interval can't be negative!";
                break;

            case "u":
            case "url":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [url]``";
                if (!VerifyUtilities.verifyUrl(args[index]))
                    return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                break;

            case "qs":
            case "quiet-start":
            case "qe":
            case "quiet-end":
            case "qr":
            case "quiet-remind":
                if (args.length > 2)
                    return "That's too many arguments for **"+args[index-1]+"**!" +
                            " Just use ``" + invoke + args[index-2] + args[index-1] + "``!";
                break;

            case "max":
            case "m":
                if (args.length > 3)
                    return "That's too many arguments for **"+args[index-1]+"**! " +
                            "Use ``"+ invoke + " " + args[index-2] + " " + args[index-1] + " [rsvp max]``";
                if (!VerifyUtilities.verifyInteger(args[index]))
                    return "The rsvp max must be a number!";
                if (Integer.valueOf(args[index])<0)
                    return "The rsvp max cannot be negative!";
                break;

            case "ex":
            case "expire":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+invoke+" "+args[index-2]+" "+args[index-1]+" [date]``";
                switch(args[index])
                {
                    case "none":
                    case "never":
                    case "null":
                        return "";

                    default:
                        if( !VerifyUtilities.verifyDate( args[index] ) )
                            return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                        if(ParsingUtilities.parseDateStr(args[index]).isBefore(LocalDate.now()))
                            return "That date is in the past!";
                        return "";
                }

            default:
                return "**" + args[index-1] + "** is not an option I know of! Please use the ``help`` command to see available options!";
        }

        return ""; // return valid
    }

    // TODO consider reworking the updateEntry command to only update the required attribute
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
        Integer rsvpMax = entry.getRsvpMax();
        ZonedDateTime expire = entry.getExpire();

        boolean quietStart = entry.isQuietStart();
        boolean quietEnd = entry.isQuietEnd();
        boolean quietRemind = entry.isQuietRemind();

        //
        // edit the event if command contains more arguments than the event ID,
        // otherwise skip this and print out the event configuration
        if(args.length > 1)
        {
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
                            {
                                comments.remove(args[index]);
                            }
                            break;
                    }

                    //Main.getEntryManager().updateEntryComments(entryId, comments);
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

                    //Main.getEntryManager().updateEntryStartTime(entryId, start);
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

                    //Main.getEntryManager().updateEntryEndTime(entryId, end);
                    break;

                case "t":
                case "title":
                    title = args[index];
                    //Main.getEntryManager().updateEntryTitle(entryId, title);
                    break;

                case "d":
                case "date":
                    LocalDate date = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                    start = start.withMonth(date.getMonthValue())
                            .withDayOfMonth(date.getDayOfMonth());
                    end = end.withMonth(date.getMonthValue())
                            .withDayOfMonth(date.getDayOfMonth());

                    //Main.getEntryManager().updateEntryDate(entryId, start, end);
                    break;

                case "sd":
                case "start date":
                case "start-date":
                    LocalDate sdate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                    start = start.withMonth(sdate.getMonthValue())
                            .withDayOfMonth(sdate.getDayOfMonth());

                    if(end.isBefore(start))
                        end = start.plusDays(1);

                    //Main.getEntryManager().updateEntryDate(entryId, start, end);
                    break;

                case "ed":
                case "end date":
                case "end-date":
                    LocalDate edate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                    end = end.withMonth(edate.getMonthValue())
                            .withDayOfMonth(edate.getDayOfMonth());

                    //Main.getEntryManager().updateEntryDate(entryId, start, end);
                    break;

                case "r":
                case "repeats":
                case "repeat":
                    repeat = ParsingUtilities.parseWeeklyRepeat(args[index].toLowerCase());
                    //Main.getEntryManager().updateEntryRepeat(entryId, repeat);
                    break;

                case "i":
                case "interval":
                    repeat = 0b10000000 | Integer.parseInt(args[index]);
                    //Main.getEntryManager().updateEntryRepeat(entryId, repeat);
                    break;

                case "u":
                case "url":
                    url = args[index];
                    //Main.getEntryManager().updateEntryUrl(entryId, url);
                    break;

                case "qs":
                case "quiet-start":
                    quietStart = !quietStart;
                    //Main.getEntryManager().updateEntryQuietStart(entryId, quietStart);
                    break;

                case "qe":
                case "quiet-end":
                    quietEnd = !quietEnd;
                    //Main.getEntryManager().updateEntryQuietEnd(entryId, quietEnd);
                    break;


                case "qr":
                case "quiet-remind":
                    quietRemind = !quietRemind;
                    //Main.getEntryManager().updateEntryQuietRemind(entryId, quietRemind);
                    break;


                case "max":
                case "m":
                    if(args[index].toLowerCase().equals("off"))
                    {
                        rsvpMax = -1;
                    }
                    else
                    {
                        rsvpMax = Integer.valueOf(args[index]);
                    }
                    //Main.getEntryManager().updateEntryRsvpMax(entryId, rsvpMax);
                    break;

                case "ex":
                case "expire":
                    switch(args[index])
                    {
                        case "none":
                        case "never":
                        case "null":
                            expire = null;
                            break;
                            
                        default:
                            expire = ZonedDateTime.of(ParsingUtilities.parseDateStr(args[index]), LocalTime.MIN, start.getZone());
                            break;
                    }
            }

            Main.getEntryManager().updateEntry(entryId, title, start, end, comments, repeat, url, entry.hasStarted(),
                    msg, entry.getGoogleId(), entry.getRsvpYes(), entry.getRsvpNo(), entry.getRsvpUndecided(),
                    quietStart, quietEnd, quietRemind, rsvpMax, expire);
        }

        //
        // send the event summary to the command channel
        //
        DateTimeFormatter dtf;
        if(Main.getScheduleManager().getClockFormat(entry.getScheduleID()).equals("24"))
            dtf = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm [z]");
        else
            dtf = DateTimeFormatter.ofPattern("yyy-MM-dd hh:mma [z]");

        String body = "Updated event :id: **"+ Integer.toHexString(entryId) +"** on <#" + entry.getScheduleID() + ">\n```js\n" +
                "Title:  \"" + title + "\"\n" +
                "Start:  " + start.format(dtf) + "\n" +
                "End:    " + end.format(dtf) + "\n" +
                "Repeat: " + MessageGenerator.getRepeatString(repeat, true) + " (" + repeat + ")" + "\n" ;

        if(url!=null)
            body += "Url: \"" + url + "\"\n";

        if(quietStart | quietEnd | quietRemind)
        {
            body += "Quiet: ";
            if(quietStart)
            {
                body += "start";
                if(quietEnd & quietRemind)
                    body += ", ";
                else if(quietEnd | quietRemind)
                    body += " and ";
            }
            if(quietEnd)
            {
                body += "end";
                if(quietRemind)
                    body += " and ";
            }
            if(quietRemind)
            {
                body += "reminders";
            }
            body += " disabled\n";
        }

        if(rsvpMax>=0)
            body += "Max: " + rsvpMax + "\n";

        if(expire != null)
            body += "Expire: \"" + expire.toLocalDate() + "\"";

        if(!comments.isEmpty())
            body += "// Comments\n";

        for(int i=1; i<comments.size()+1; i++)
        {
            body += "[" + i + "] \"" + comments.get(i-1) + "\"\n";
        }
        body += "```";

        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
