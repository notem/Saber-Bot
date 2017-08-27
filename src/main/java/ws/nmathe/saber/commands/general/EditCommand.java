package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.MessageGenerator;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 */
public class EditCommand implements Command
{
    @Override
    public String name()
    {
        return "edit";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <ID> <option> <arg>```\n" +
                "The edit command will allow you to change an event's settings." +
                "\n\n" +
                "``<option>`` is to contain which attribute of the event you wish to edit. ``<arg>`` should be the" +
                " new configuration." +
                "\n\n```diff\n+ Options ```\n" +
                "List of ``<option>``s: ``start``, ``end``, ``title``, ``comment``, ``date``, " +
                "``start-date``, ``end-date``, ``repeat``, ``interval``, ``url``, ``quiet-start``, ``quiet-end``, ``quiet-remind``, ``expire``" +
                " and ``max``.\n\n" +
                "Most of the options listed above accept the same arguments as the ``create`` command." +
                "\nReference the ``help`` information for the ``create`` command for more information." +
                "\n\n" +
                "The comment option requires one additional argument immediately after the 'comment' argument.\n" +
                "This argument identifies what comment operation to do. The operations are ``add``, ``remove``, and ``swap``." +
                "\nSee the examples for their usage." +
                "\n\n" +
                "Announcements for individual events can be toggled on-off using any of these three options: " +
                "``quiet-start``, ``quiet-end``, ``quiet-remind``\n" +
                "No additional arguments need to be provided when using one of the ``quiet-`` options." +
                "\n\nsplithere" +
                "If the schedule that the event is placed on is rsvp enabled (which may be turned on using the ``config`` command)" +
                " a limit to the number of users who may rsvp 'yes' can be set using the ``max`` option.\n" +
                "The ``max`` option requires one additional argument, which is the maximum number of players allowed to rsvp for the event.\n" +
                "Use \"off\" as the argument to remove a previously set limit." +
                "\n\n" +
                "The thumbnail and image of the event's discord embed can be set through the 'thumbnail' and 'image' options.\n" +
                "Provide a full url direct link to the image as the argument.\n" +
                "The thumbnail of the event should appear as a small image to the right of the event's description.\n" +
                "The image of the event should appear as a full-size image below the main content.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " 3fa0dd0 comment add \"Attendance is mandatory\"``" +
                "\n``" + head + " 80c0sd09 comment remove 3``" +
                "\n``" + head + " 09adff3 comment swap 1 2" +
                "\n``" + head + " 0abf2991 start 21:15``" +
                "\n``" + head + " 49afaf2 end 2:15pm``" +
                "\n``" + head + " 409fa22 start-date 10/9``" +
                "\n``" + head + " a00af9a repeat \"Sun, Tue, Fri\"``" +
                "\n``" + head + " 0912af9 quiet-start``" +
                "\n``" + head + " a901992 expire 2019/1/1" +
                "";

        String USAGE_BRIEF = "``" + head + "`` - modify an event";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        int index = 0;

        if( args.length < 1 )
            return "That's not enough arguments! Use ``" + head + " <ID> [<option> <arg>]``";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "``" + args[index] + "`` is not a valid entry ID!";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
            return "I could not find an entry with that ID!";

        if(Main.getScheduleManager().isLocked(entry.getScheduleID()))
            return "Schedule is locked for sorting/syncing. Please try again after sort/sync finishes.";

        if(args.length == 1)
            return "";

        index++; // 1

        // check later args
        switch( args[index++].toLowerCase() ) // 2
        {
            case "c":
            case "comment":
            case "comments":
                if(args.length <= index+1)
                    return "That's not enough arguments for *comment*! Use ``"+ head +" [id] comment [add|remove] \"comment\"``";

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
                        if(VerifyUtilities.verifyInteger(args[index]))
                        {
                            Integer it = Integer.parseInt(args[index]);
                            if(it > entry.getComments().size())
                            {
                                return "The event doesn't have a comment number " + it + "!";
                            }
                            if(it < 1)
                            {
                                return "The comment number must be above 0!";
                            }
                        }
                        break;
                    case "s":
                    case "swap":
                        if(args.length != 5)
                            return "That's not enough arguments for *comment swap*!" +
                                    "\nUse ``"+ head +" [id] comment swap [number] [number]``";
                        if(!VerifyUtilities.verifyInteger(args[index]))
                        {
                            return "Argument **" + args[index] + "** is not a number!";
                        }
                        if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
                        {
                            return "Comment **#" + args[index] + "** does not exist!";
                        }
                        index++; // 4
                        if(!VerifyUtilities.verifyInteger(args[index]))
                        {
                            return "Argument **" + args[index] + "** is not a number!";
                        }
                        if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
                        {
                            return "Comment **#" + args[index] + "** does not exist!";
                        }
                        break;
                    default:
                        return "The only valid options for ``comment`` are **add**, **remove**, or **swap**!";
                }
                break;

            case "s":
            case "starts":
            case "start":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``" + head + " "+args[index-2]+" "+args[index-1]+" [start time]``";
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
                            "Use ``" + head + " "+args[index-2]+" "+args[index-1]+" [end time]``";
                if( !VerifyUtilities.verifyTime( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
                break;

            case "t":
            case "title":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [\"title\"]``";
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
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [date]``";
                if( !VerifyUtilities.verifyDate( args[index] ) )
                    return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";

                ZoneId zone = Main.getScheduleManager().getTimeZone(entry.getScheduleID());
                ZonedDateTime time = ZonedDateTime.of(ParsingUtilities.parseDateStr(args[index]), LocalTime.now(zone), zone);
                if(time.isBefore(ZonedDateTime.now()))
                {
                    return "That date is in the past!";
                }

                if(entry.hasStarted())
                    return "You cannot modify the date of events which have already started!";
                break;

            case "r":
            case "repeats":
            case "repeat":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [repeat]``";
                break;

            case "i":
            case "interval":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [number]``";
                if(!VerifyUtilities.verifyInteger(args[index]))
                    return "**" + args[index] + "** is not a number!";
                if(Integer.parseInt(args[index]) < 1)
                    return "Your repeat interval can't be negative!";
                break;

            case "u":
            case "url":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [url]``";
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
                            " Just use ``" + head + args[index-2] + args[index-1] + "``!";
                break;

            case "max":
            case "m":
                if (args.length > 3)
                    return "That's too many arguments for **"+args[index-1]+"**! " +
                            "Use ``"+ head + " " + args[index-2] + " " + args[index-1] + " [rsvp max]``";
                if (!VerifyUtilities.verifyInteger(args[index]))
                    return "The rsvp max must be a number!";
                if (Integer.valueOf(args[index])<0)
                    return "The rsvp max cannot be negative!";
                break;

            case "ex":
            case "expire":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [date]``";
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

            case "im":
            case "image":
            case "th":
            case "thumbnail":
                if(args.length != 3)
                    return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                            "Use ``"+head+" "+args[index-2]+" "+args[index-1]+" [url]``";
                switch(args[index])
                {
                    case "off":
                    case "null":
                        break;

                    default:
                        if (!VerifyUtilities.verifyUrl(args[index]))
                            return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                }
                break;

            default:
                return "**" + args[index-1] + "** is not an option I know of! Please use the ``help`` command to see available options!";
        }

        return ""; // return valid
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        try
        {
            int index = 0;

            Integer entryId = Integer.decode( "0x" + args[index] );
            ScheduleEntry se = Main.getEntryManager().getEntry( entryId );

            Message msg = se.getMessageObject();
            if( msg==null ) return;


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
                        ArrayList<String> comments = se.getComments();
                        switch( args[index++] )   // 3
                        {
                            case "a":
                            case "add" :
                                comments.add( args[index] );
                                se.setComments(comments);
                                break;
                            case "r":
                            case "remove" :
                                if(VerifyUtilities.verifyInteger(args[index]))
                                {
                                    comments.remove( Integer.parseInt(args[index])-1 );
                                }
                                else
                                {
                                    comments.remove(args[index]);
                                }
                                se.setComments(comments);
                                break;
                            case "s":
                            case "swap":
                                String a = comments.get(Integer.parseInt(args[index])-1);
                                String b = comments.get(Integer.parseInt(args[index+1])-1);
                                comments.set(Integer.parseInt(args[index])-1, b);
                                comments.set(Integer.parseInt(args[index+1])-1, a);
                                se.setComments(comments);
                                break;
                        }
                        break;

                    case "s":
                    case "starts":
                    case "start":
                        ZonedDateTime newStart = ParsingUtilities.parseTime(se.getStart(), args[index]);
                        se.setStart(newStart);

                        if(ZonedDateTime.now().isAfter(se.getStart())) //add a day if the time has already passed
                        {
                            se.setStart(se.getStart().plusDays(1));
                        }
                        if(se.getStart().isAfter(se.getEnd()))        //add a day to end if end is after start
                        {
                            se.setEnd(se.getEnd().plusDays(1));
                        }
                        break;

                    case "e":
                    case "ends":
                    case "end":
                        ZonedDateTime newEnd = ParsingUtilities.parseTime(se.getEnd(), args[index]);
                        se.setEnd(newEnd);

                        if(ZonedDateTime.now().isAfter(se.getEnd()))
                        { // add a day if the time has already passed
                            se.setEnd(se.getEnd().plusDays(1));
                        }
                        if(se.getStart().isAfter(se.getEnd()))
                        { // add a day to end if end is after start
                            se.setEnd(se.getEnd().plusDays(1));
                        }
                        break;

                    case "t":
                    case "title":
                        se.setTitle(args[index]);
                        break;

                    case "d":
                    case "date":
                        LocalDate date = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                        se.setStart(se.getStart()
                                .withMonth(date.getMonthValue())
                                .withDayOfMonth(date.getDayOfMonth()));
                        se.setEnd(se.getEnd()
                                .withMonth(date.getMonthValue())
                                .withDayOfMonth(date.getDayOfMonth()));
                        break;

                    case "sd":
                    case "start date":
                    case "start-date":
                        LocalDate sdate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                        se.setStart(se.getStart()
                                .withMonth(sdate.getMonthValue())
                                .withDayOfMonth(sdate.getDayOfMonth()));

                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setEnd(se.getStart());
                        }
                        break;

                    case "ed":
                    case "end date":
                    case "end-date":
                        LocalDate edate = ParsingUtilities.parseDateStr(args[index].toLowerCase());

                        se.setEnd(se.getEnd()
                                .withMonth(edate.getMonthValue())
                                .withDayOfMonth(edate.getDayOfMonth()));

                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setStart(se.getEnd());
                        }
                        break;

                    case "r":
                    case "repeats":
                    case "repeat":
                        se.setRepeat(ParsingUtilities.parseWeeklyRepeat(args[index].toLowerCase()));
                        break;

                    case "i":
                    case "interval":
                        se.setRepeat(0b10000000 | Integer.parseInt(args[index]));
                        break;

                    case "u":
                    case "url":
                        se.setTitleUrl(args[index]);
                        break;

                    case "qs":
                    case "quiet-start":
                        se.setQuietStart(!se.isQuietStart());
                        break;

                    case "qe":
                    case "quiet-end":
                        se.setQuietEnd(!se.isQuietEnd());
                        break;

                    case "qr":
                    case "quiet-remind":
                        se.setQuietRemind(!se.isQuietRemind());
                        break;

                    case "max":
                    case "m":
                        if(args[index].toLowerCase().equals("off"))
                        {
                            se.setRsvpMax(-1);
                        }
                        else
                        {
                            se.setRsvpMax(Integer.valueOf(args[index]));
                        }
                        break;

                    case "ex":
                    case "expire":
                        switch(args[index])
                        {
                            case "off":
                            case "none":
                            case "never":
                            case "null":
                                se.setExpire(null);
                                break;

                            default:
                                se.setExpire(ZonedDateTime.of(ParsingUtilities.parseDateStr(args[index]),
                                        LocalTime.MIN, se.getStart().getZone()));
                                break;
                        }

                    case "im":
                    case "image":
                        switch(args[index])
                        {
                            case "null":
                            case "off":
                                se.setImageUrl(null);
                                break;
                            default:
                                se.setImageUrl(args[index]);
                                break;
                        }
                        break;

                    case "th":
                    case "thumbnail":
                        switch(args[index])
                        {
                            case "null":
                            case "off":
                                se.setThumbnailUrl(null);
                                break;
                            default:
                                se.setThumbnailUrl(args[index]);
                                break;
                        }
                        break;
                }

                Main.getEntryManager().updateEntry(se);
            }

            //
            // send the event summary to the command channel
            //
            DateTimeFormatter dtf;
            if(Main.getScheduleManager().getClockFormat(se.getScheduleID()).equals("24"))
            {
                dtf = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm [z]");
            }
            else
            {
                dtf = DateTimeFormatter.ofPattern("yyy-MM-dd hh:mma [z]");
            }

            String body = "Updated event :id: **"+ Integer.toHexString(entryId) +"** on <#" + se.getScheduleID() + ">\n```js\n" +
                    "Title:  \"" + se.getTitle() + "\"\n" +
                    "Start:  " + se.getStart().format(dtf) + "\n" +
                    "End:    " + se.getEnd().format(dtf) + "\n" +
                    "Repeat: " + MessageGenerator.getRepeatString(se.getRepeat(), true) + " (" + se.getRepeat() + ")" + "\n";

            if(se.getTitleUrl()!=null)
            {
                body += "Url: \"" + se.getTitleUrl() + "\"\n";
            }

            if(se.isQuietRemind() | se.isQuietEnd() | se.isQuietStart())
            {
                body += "Quiet: ";
                if(se.isQuietStart())
                {
                    body += "start";
                    if(se.isQuietEnd() & se.isQuietRemind())
                        body += ", ";
                    else if(se.isQuietEnd() | se.isQuietRemind())
                        body += " and ";
                }
                if(se.isQuietEnd())
                {
                    body += "end";
                    if(se.isQuietRemind())
                        body += " and ";
                }
                if(se.isQuietRemind())
                {
                    body += "reminders";
                }
                body += " disabled\n";
            }

            if(se.getRsvpMax()>=0)
                body += "Max: " + se.getRsvpMax() + "\n";

            if(se.getExpire() != null)
                body += "Expire: \"" + se.getExpire().toLocalDate() + "\"\n";

            if(se.getImageUrl() != null)
                body += "Image: \"" + se.getImageUrl() + "\"\n";

            if(se.getThumbnailUrl() != null)
                body += "Thumbnail: \"" + se.getThumbnailUrl() + "\"\n";

            if(!se.getComments().isEmpty())
                body += "// Comments\n";

            for(int i=1; i<se.getComments().size()+1; i++)
            {
                body += "[" + i + "] \"" + se.getComments().get(i-1) + "\"\n";
            }
            body += "```";

            MessageUtilities.sendMsg(body, event.getChannel(), null);
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
