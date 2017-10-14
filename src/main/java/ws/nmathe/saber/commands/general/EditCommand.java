package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

/**
 * used to edit currently active events
 */
public class EditCommand implements Command
{
    @Override
    public String name()
    {
        return "edit";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - modify an event";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.CORE);

        String cat1 = "- Usage\n" + head + " <ID> [<option> <arg(s)>]";
        String cont1 = "The edit command will allow you to change an event's settings." +
                "\n\n" +
                "``<option>`` is to contain which attribute of the event you wish to edit. ``<arg>`` should be the" +
                " new configuration." +
                "\n\n```diff\n+ Options ```\n" +
                "List of ``<option>``s: ``start``, ``end``, ``title``, ``comment``, ``date``, " +
                "``start-date``, ``end-date``, ``repeat``, ``interval``, ``url``, ``quiet-start``, ``quiet-end``, ``quiet-remind``, ``expire``, ``deadline``," +
                " and ``limit``.\n\n" +
                "Most of the options listed above accept the same arguments as the ``create`` command.\n" +
                "Reference the ``help`` information for the ``create`` command for more information.\n" +
                "Similar to the ``create`` command, any number of [<option> <arg(s)>] pairs can be appended to the command." +
                "\n\n" +
                "The comment option requires one additional argument immediately after the 'comment' argument.\n" +
                "This argument identifies what comment operation to do. The operations are ``add``, ``remove``, and ``swap``." +
                "\nSee the examples for their usage.";
        info.addUsageCategory(cat1, cont1);

        String cat2 = "+ Announcement Silencing";
        String cont2 = "Announcements for individual events can be toggled on-off using any of these three options: " +
                "``quiet-start``, ``quiet-end``, ``quiet-remind``\n" +
                "No additional arguments need to be provided when using one of the ``quiet-`` options.";
        info.addUsageCategory(cat2, cont2);

        String cat3 = "+ RSVP Limits";
        String cont3 = "If the schedule that the event is placed on is rsvp enabled (which may be turned on using the ``config`` command)" +
                " a limit to the number of users who may rsvp as a particular group can be set using the ``limit`` option.\n" +
                "The ``limit`` option requires two additional arguments: the first of which should be the name of the rsvp group to " +
                "limit and the second argument should the be number of max individuals allowed to rsvp for that group.\n" +
                "Use \"off\" as the argument to remove a previously set limit.";
        info.addUsageCategory(cat3, cont3);

        String cat4 = "+ Image and Thumbnail";
        String cont4 = "The thumbnail and image of the event's discord embed can be set through the 'thumbnail' and 'image' options.\n" +
                "Provide a full url direct link to the image as the argument.\n" +
                "The thumbnail of the event should appear as a small image to the right of the event's description.\n" +
                "The image of the event should appear as a full-size image below the main content.";
        info.addUsageCategory(cat4, cont4);

        info.addUsageExample(head + " 9aA4/K comment add \"Attendance is mandatory\"");
        info.addUsageExample(head + " 9aA4/K comment remove 3");
        info.addUsageExample(head + " 9aA4/K comment swap 1 2");
        info.addUsageExample(head + " AJ@29l start 21:15");
        info.addUsageExample(head + " AJ@29l end 2:15pm");
        info.addUsageExample(head + " AJ@29l start-date 10/9");
        info.addUsageExample(head + " AJ@29l repeat \"Sun, Tue, Fri\"");
        info.addUsageExample(head + " AJ@29l quiet-start");
        info.addUsageExample(head + " AJ@29l expire 2019/1/1");
        info.addUsageExample(head + " AJ@29l limit Yes 15");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        if( args.length < 1 )
        {
            return "That's not enough arguments! Use ``" + head + " <ID> [<option> <arg>]``";
        }

        // check first arg
        if( !VerifyUtilities.verifyEntryID(args[index]) )
        {
            return "``" + args[index] + "`` is not a valid entry ID!";
        }

        Integer Id = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );
        if(entry == null)
        {
            return "I could not find an entry with that ID!";
        }
        if(Main.getScheduleManager().isLocked(entry.getChannelId()))
        {
            return "Schedule is locked for sorting/syncing. Please try again after sort/sync finishes.";
        }

        // if only one argument, command is valid
        if(args.length == 1) return "";

        index++; // 1

        // check later args
        ZoneId zone = Main.getScheduleManager().getTimeZone(entry.getChannelId());
        String verify;
        while(index < args.length)
        {
            switch(args[index++].toLowerCase())
            {
                case "c":
                case "comment":
                case "comments":
                    if(args.length-index < 1)
                    {
                        return "That's not enough arguments for *comment*!\n" +
                                "Use ``"+head+" "+args[0]+" "+args[index-1]+" [add|remove|swap] <arg(s)>``";
                    }
                    switch (args[index++].toLowerCase())
                    {
                        case "a":
                        case "add":
                            if(args.length-index < 1)
                            {
                                return "That's not enough arguments for *comment add*!\n" +
                                        "Use ``"+head+" "+args[0]+" "+args[index-2]+" "+args[index-1]+" \"your comment\"``";
                            }
                            if(args[index].length() > 1024) return "Comments should not be larger than 1024 characters!";
                            index++;
                            break;
                        case "r":
                        case "remove":
                            if(args.length-index < 1)
                            {
                                return "That's not enough arguments for *comment remove*!\n" +
                                        "Use ``"+head+" "+args[0]+" "+args[index-2]+" "+args[index-1]+" [number]``";
                            }
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
                            index++;
                            break;
                        case "s":
                        case "swap":
                            if(args.length-index < 2)
                            {
                                return "That's not enough arguments for *comment swap*!" +
                                        "\nUse ``"+ head +" "+args[0]+" "+args[index-2]+" "+args[index-1]+" [number] [number]``";
                            }
                            if(!VerifyUtilities.verifyInteger(args[index]))
                            {
                                return "Argument **" + args[index] + "** is not a number!";
                            }
                            if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
                            {
                                return "Comment **#" + args[index] + "** does not exist!";
                            }
                            index++;
                            if(!VerifyUtilities.verifyInteger(args[index]))
                            {
                                return "Argument **" + args[index] + "** is not a number!";
                            }
                            if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
                            {
                                return "Comment **#" + args[index] + "** does not exist!";
                            }
                            index += 2;
                            break;
                        default:
                            return "The only valid options for ``comment`` are **add**, **remove**, or **swap**!";
                    }
                    break;

                case "s":
                case "starts":
                case "start":
                    if(args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**!\n" +
                                "Use ``" + head + " "+args[index-2]+" "+args[index-1]+" [start time]``";
                    }
                    if(!VerifyUtilities.verifyTime(args[index]))
                    {
                        return "I could not understand **" + args[index] + "** as a time!" +
                                "\nPlease use the format hh:mm[am|pm].";
                    }
                    if(ZonedDateTime.of(entry.getStart().toLocalDate(), ParsingUtilities.parseTime(args[index]), entry.getStart().getZone()).isBefore(ZonedDateTime.now()))
                    {
                        return "Today's time is already past *" + args[index] + "*!\n" +
                                "Please use a different time, or change the date for the event!";
                    }
                    if(entry.hasStarted())
                    {
                        return "You cannot modify the start time after the event has already started.";
                    }
                    index++;
                    break;

                case "e":
                case "ends":
                case "end":
                    if(args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                                "Use ``" + head + " "+args[0]+" "+args[index-1]+" [end time]``";
                    }
                    // if time is not "off" or an invalid time, fail
                    if (!VerifyUtilities.verifyTime(args[index]) && !args[index].equalsIgnoreCase("off"))
                    {
                        return "I could not understand **" + args[index] + "** as a time!\n" +
                                "Please use the format hh:mm[am|pm].";
                    }
                    // if time is not "off" do additional check
                    if(!args[index].equalsIgnoreCase("off"))
                    {
                        ZonedDateTime end = ZonedDateTime.of(entry.getEnd().toLocalDate(),
                                ParsingUtilities.parseTime(args[index]),
                                entry.getEnd().getZone());
                        if (end.isBefore(ZonedDateTime.now()))
                        {
                            return "Today's time is already past *" + args[index] + "*!\n" +
                                    "Please use a different time, or change the date for the event!";
                        }
                    }
                    index++;
                    break;

                case "t":
                case "title":
                    if(args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                                "Use ``"+head+" "+args[0]+" "+args[index-1]+" [\"title\"]``";
                    }
                    if( args[index].length() > 255 )
                    {
                        return "Your title can be at most 255 characters!";
                    }
                    index++;
                    break;

                case "d":
                case "date":
                case "sd":
                case "start-date":
                case "ed":
                case "end-date":
                    verify = VerifyUtilities.verifyDate(args, index, head, entry, zone);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    index++;
                    break;

                case "r":
                case "repeats":
                case "repeat":
                    verify = VerifyUtilities.verifyRepeat(args, index, head);
                    if (!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "i":
                case "interval":
                    verify = VerifyUtilities.verifyInterval(args, index, head);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "im":
                case "image":
                case "th":
                case "thumbnail":
                case "u":
                case "url":
                    verify = VerifyUtilities.verifyUrl(args, index, head);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "qs":
                case "quiet-start":
                case "qe":
                case "quiet-end":
                case "qr":
                case "quiet-remind":
                case "quiet-all":
                case "qa":
                    break;

                case "ex":
                case "expire":
                    verify = VerifyUtilities.verifyExpire(args, index, head, zone);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "limit":
                case "l":
                    verify = VerifyUtilities.verifyLimit(args, index, head, entry);
                    if(!verify.isEmpty()) return verify;
                    index+=2;
                    break;

                case "deadline":
                case "dl":
                    verify = VerifyUtilities.verifyDeadline(args, index, head);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "a":
                case "an":
                case "announce":
                case "announcement":
                case "announcements":
                    verify = VerifyUtilities.verifyAnnouncementTime(args, index, head, event);
                    if(!verify.isEmpty()) return verify;
                    index += 4;
                    break;

                default:
                    return "**" + args[index-1] + "** is not an option I know of!\n" +
                            "Please use the ``help`` command to see available options!";
            }
        }

        return ""; // return valid
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        Integer entryId = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry se = Main.getEntryManager().getEntry( entryId );

        Message msg = se.getMessageObject();
        if( msg==null ) return;

        //
        // edit the event if command contains more arguments than the event ID,
        // otherwise skip this and print out the event configuration
        if(args.length > 1)
        {
            index++;    // 1
            while(index < args.length)
            {
                ZoneId zone = Main.getScheduleManager().getTimeZone(se.getChannelId());
                switch( args[index++] )
                {
                    case "c":
                    case "comment":
                        ArrayList<String> comments = se.getComments();
                        switch( args[index++] )
                        {
                            case "a":
                            case "add" :
                                comments.add( args[index] );
                                se.setComments(comments);
                                index++;
                                break;
                            case "r":
                            case "remove" :
                                if(VerifyUtilities.verifyInteger(args[index]))
                                {
                                    comments.remove(Integer.parseInt(args[index])-1);
                                }
                                else
                                {
                                    comments.remove(args[index]);
                                }
                                se.setComments(comments);
                                index++;
                                break;
                            case "s":
                            case "swap":
                                String a = comments.get(Integer.parseInt(args[index])-1);
                                String b = comments.get(Integer.parseInt(args[index+1])-1);
                                comments.set(Integer.parseInt(args[index])-1, b);
                                comments.set(Integer.parseInt(args[index+1])-1, a);

                                se.setComments(comments);
                                index += 2;
                                break;
                        }
                        break;

                    case "s":
                    case "starts":
                    case "start":
                        // create new datetime and update the schedule entry object
                        ZonedDateTime newStart = ZonedDateTime.of(se.getStart().toLocalDate(),
                                ParsingUtilities.parseTime(args[index]), se.getStart().getZone());
                        se.setStart(newStart);

                        // do some final processing on start/end times
                        if(ZonedDateTime.now().isAfter(se.getStart()))
                        {
                            //add a day if the time has already passed
                            se.setStart(se.getStart().plusDays(1));
                        }
                        if(se.getStart().isAfter(se.getEnd()))
                        {
                            //add a day to end if end is after start
                            se.setEnd(se.getEnd().plusDays(1));

                            // reload end reminders
                            se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        }

                        // reload start reminders
                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()));
                        index++;
                        break;

                    case "e":
                    case "ends":
                    case "end":
                        // if off, use start
                        if(args[index].equalsIgnoreCase("off"))
                        {
                            se.setEnd(se.getStart());
                        }
                        else
                        {
                            // otherwise parse the input for a time
                            se.setEnd(ZonedDateTime.of(se.getEnd().toLocalDate(),
                                        ParsingUtilities.parseTime(args[index]),
                                        se.getEnd().getZone()));
                        }

                        // add a day if the time has already passed
                        if(ZonedDateTime.now().isAfter(se.getEnd())) se.setEnd(se.getEnd().plusDays(1));

                        // add a day to end if end is after start
                        if(se.getStart().isAfter(se.getEnd())) se.setEnd(se.getEnd().plusDays(1));

                        // reload end reminders
                        se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        index++;
                        break;

                    case "t":
                    case "title":
                        se.setTitle(args[index]);
                        index++;
                        break;

                    case "d":
                    case "date":
                        ZonedDateTime date = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setStart(se.getStart()
                                .withMonth(date.getMonthValue())
                                .withDayOfMonth(date.getDayOfMonth())
                                .withYear(date.getYear()));
                        se.setEnd(se.getEnd()
                                .withMonth(date.getMonthValue())
                                .withDayOfMonth(date.getDayOfMonth())
                                .withYear(date.getYear()));

                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                                .reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        index++;
                        break;

                    case "sd":
                    case "start date":
                    case "start-date":
                        ZonedDateTime sdate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setStart(se.getStart()
                                .withMonth(sdate.getMonthValue())
                                .withDayOfMonth(sdate.getDayOfMonth())
                                .withYear(sdate.getYear()));

                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setEnd(se.getStart());
                            se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        }

                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()));
                        index++;
                        break;

                    case "ed":
                    case "end date":
                    case "end-date":
                        ZonedDateTime edate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setEnd(se.getEnd()
                                .withMonth(edate.getMonthValue())
                                .withDayOfMonth(edate.getDayOfMonth())
                                .withYear(edate.getYear()));

                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setStart(se.getEnd());
                            se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()));
                        }

                        se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        index++;
                        break;

                    case "r":
                    case "repeats":
                    case "repeat":
                        se.setRepeat(ParsingUtilities.parseRepeat(args[index].toLowerCase()));
                        index++;
                        break;

                    case "i":
                    case "interval":
                        se.setRepeat(ParsingUtilities.parseInterval(args[index]));
                        index++;
                        break;

                    case "u":
                    case "url":
                        se.setTitleUrl(ParsingUtilities.parseUrl(args[index]));
                        index++;
                        break;

                    case "im":
                    case "image":
                        se.setImageUrl(ParsingUtilities.parseUrl(args[index]));
                        index++;
                        break;

                    case "th":
                    case "thumbnail":
                        se.setThumbnailUrl(ParsingUtilities.parseUrl(args[index]));
                        index++;
                        break;

                    case "ex":
                    case "expire":
                        se.setExpire(ParsingUtilities.parseNullableDate(args[index], zone));
                        index++;
                        break;

                    case "deadline":
                    case "dl":
                        se.setRsvpDeadline(ParsingUtilities.parseNullableDate(args[index], zone));
                        index++;
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

                    case "qa":
                    case "quiet-all":
                        if(se.isQuietRemind() && se.isQuietEnd() && se.isQuietStart())
                        {
                            se.setQuietRemind(false).setQuietEnd(false).setQuietStart(false);
                        }
                        else
                        {
                            se.setQuietRemind(true).setQuietEnd(true).setQuietStart(true);
                        }
                        break;

                    case "limit":
                    case "l":
                        Integer lim = null;
                        if(!args[index].equalsIgnoreCase("off"))
                        {
                            lim = Integer.parseInt(args[index+1]);
                        }
                        se.setRsvpLimit(args[index], lim);
                        index += 2;
                        break;

                    case "a":
                    case "an":
                    case "announce":
                    case "announcement":
                    case "announcements":
                        switch(args[index++].toLowerCase())
                        {
                            default:
                                index++;
                                break;

                            case "a":
                            case "add":
                                String target = args[index].replaceAll("[^\\d]","");
                                String time = args[index+1];
                                String message = args[index+2];
                                se.addAnnouncementOverride(target, time, message);
                                index += 4;
                                break;

                            case "r":
                            case "remove":
                                Integer id = Integer.parseInt(args[index].replaceAll("[^\\d]",""))-1;
                                se.removeAnnouncementOverride(id);
                                index ++;
                                break;
                        }
                        break;
                }
            }
            Main.getEntryManager().updateEntry(se, true);
        }

        //
        // send the event summary to the command channel
        //
        String body = "Updated event :id: **"+ ParsingUtilities.intToEncodedID(se.getId()) +"** on <#" + se.getChannelId() + ">\n" +
                "```js\n" + se.toString() + "\n```";
        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
