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

    // TODO consider reworking the updateEntry command to only update the required attribute
    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        Integer entryId = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

        Message msg = entry.getMessageObject();
        if( msg==null ) return;

        // initialize variables as the current (old) entry settings
        String title = entry.getTitle();
        ArrayList<String> comments = entry.getComments();
        ZonedDateTime start = entry.getStart();
        ZonedDateTime end = entry.getEnd();
        int repeat = entry.getRepeat();
        Integer rsvpMax = entry.getRsvpMax();
        ZonedDateTime expire = entry.getExpire();

        boolean quietStart = entry.isQuietStart();
        boolean quietEnd = entry.isQuietEnd();
        boolean quietRemind = entry.isQuietRemind();

        String url = entry.getTitleUrl();
        String imageUrl = entry.getImageUrl();
        String thumbnailUrl = entry.getThumbnailUrl();

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
                            if(VerifyUtilities.verifyInteger(args[index]))
                            {
                                comments.remove( Integer.parseInt(args[index])-1 );
                            }
                            else
                            {
                                comments.remove(args[index]);
                            }
                            break;
                        case "s":
                        case "swap":
                            String a = comments.get(Integer.parseInt(args[index])-1);
                            String b = comments.get(Integer.parseInt(args[index+1])-1);
                            comments.set(Integer.parseInt(args[index])-1, b);
                            comments.set(Integer.parseInt(args[index+1])-1, a);
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
                    repeat = ParsingUtilities.parseWeeklyRepeat(args[index].toLowerCase());
                    break;

                case "i":
                case "interval":
                    repeat = 0b10000000 | Integer.parseInt(args[index]);
                    break;

                case "u":
                case "url":
                    url = args[index];
                    break;

                case "qs":
                case "quiet-start":
                    quietStart = !quietStart;
                    break;

                case "qe":
                case "quiet-end":
                    quietEnd = !quietEnd;
                    break;

                case "qr":
                case "quiet-remind":
                    quietRemind = !quietRemind;
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
                    break;

                case "ex":
                case "expire":
                    switch(args[index])
                    {
                        case "off":
                        case "none":
                        case "never":
                        case "null":
                            expire = null;
                            break;
                            
                        default:
                            expire = ZonedDateTime.of(ParsingUtilities.parseDateStr(args[index]), LocalTime.MIN, start.getZone());
                            break;
                    }

                case "im":
                case "image":
                    switch(args[index])
                    {
                        case "null":
                        case "off":
                            imageUrl = null;
                            break;
                        default:
                            imageUrl = args[index];
                            break;
                    }
                    break;

                case "th":
                case "thumbnail":
                    switch(args[index])
                    {
                        case "null":
                        case "off":
                            thumbnailUrl = null;
                            break;
                        default:
                            thumbnailUrl = args[index];
                            break;
                    }
                    break;
            }

            Main.getEntryManager().updateEntry(entryId, title, start, end, comments, repeat, url, entry.hasStarted(),
                    msg, entry.getGoogleId(), entry.getRsvpYes(), entry.getRsvpNo(), entry.getRsvpUndecided(),
                    quietStart, quietEnd, quietRemind, rsvpMax, expire, imageUrl, thumbnailUrl);
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
                "Repeat: " + MessageGenerator.getRepeatString(repeat, true) + " (" + repeat + ")" + "\n";

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
            body += "Expire: \"" + expire.toLocalDate() + "\"\n";

        if(imageUrl != null)
            body += "Image: \"" + imageUrl + "\"\n";

        if(thumbnailUrl != null)
            body += "Thumbnail: \"" + thumbnailUrl + "\"\n";

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
