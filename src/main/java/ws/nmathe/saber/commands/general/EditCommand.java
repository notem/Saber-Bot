package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.EntryManager;
import ws.nmathe.saber.core.schedule.EventRecurrence;
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
import java.util.ArrayList;
import java.util.Map;

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
                "``start-date``, ``end-date``, ``repeat``, ``url``, ``quiet-start``, ``quiet-end``, " +
                "``quiet-remind``, ``expire``, ``deadline``, ``count``," +
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
        info.addUsageExample(head + " J09DlA announcement add #general start-1h \"Get ready! **%t** begins in one hour!\"");
        info.addUsageExample(head +  "J09DlA announcement remove 1");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        if(args.length < 1)
        {
            return "That's not enough arguments! Use ``" + head + " <ID> [<option> <arg>]``";
        }

        // check first arg
        if(!VerifyUtilities.verifyEntryID(args[index]))
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
            return "This schedule is locked. Please try again after the sort/sync operation finishes.";
        }

        TextChannel channel = event.getGuild().getTextChannelById(entry.getChannelId());
        if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(channel, Permission.MESSAGE_HISTORY))
        {
            return "The bot must have the \"Read Message History\" permission on channel for which that event exists!";
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
                            verify = VerifyUtilities.verifyCommentAdd(args, index, head);
                            if(!verify.isEmpty()) return verify;
                            index++;
                            break;
                        case "r":
                        case "remove":
                            verify = VerifyUtilities.verifyCommentRemove(args, index, head, entry);
                            if(!verify.isEmpty()) return verify;
                            index++;
                            break;
                        case "s":
                        case "swap":
                            verify = VerifyUtilities.verifyCommentSwap(args, index, head, entry);
                            if(!verify.isEmpty()) return verify;
                            index += 2;
                            break;
                        case "m":
                        case "modify":
                        case "replace":
                        case "set":
                            if (args.length-index < 2)
                            {
                                return "That's not the right number of arguments for **" + args[index-1] +"**!\n" +
                                        "Use ``" + head + " " + args[index-2] + " " + args[index-1] + " [num] [new_comment]``!";
                            }
                            if (!VerifyUtilities.verifyInteger(args[index]))
                            {
                                return "The argument **"+args[index]+"** is not right!\n" +
                                        "This needs to be a number representing the comment number" +
                                        " you wish to replace!";
                            }
                            int num = Integer.parseInt(args[index]);
                            if (entry.getComments().size() > num || num < 0)
                            {
                                return "The provided comment number must be between 1 and " +
                                        entry.getComments().size() + "!";
                            }
                            index += 2;
                            break;
                        default:
                            return "The only valid options for ``comment`` are **add**, **remove**, **modify**, or **swap**!";
                    }
                    break;

                case "desc":
                case "description":
                    if (args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**!\n"+
                                "Use ``" + head + " " + args[index-2] + " " + args[index-1] + " [description]``";
                    }
                    index++;
                    break;

                case "s":
                case "starts":
                case "start":
                    if (args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**!\n" +
                                "Use ``" + head + " "+args[index-2]+" "+args[index-1]+" [start time]``";
                    }
                    if (!VerifyUtilities.verifyTime(args[index]))
                    {
                        return "I could not understand **" + args[index] + "** as a time!" +
                                "\nPlease use the format hh:mm[am|pm].";
                    }
                    if (ZonedDateTime.of(entry.getStart().toLocalDate(), ParsingUtilities.parseTime(args[index]), entry.getStart().getZone()).isBefore(ZonedDateTime.now()))
                    {
                        return "Today's time is already past *" + args[index] + "*!\n" +
                                "Please use a different time, or change the date for the event!";
                    }
                    if (entry.hasStarted())
                    {
                        return "You cannot modify the start time after the event has already started.";
                    }
                    index++;
                    break;

                case "e":
                case "ends":
                case "end":
                    if (args.length-index < 1)
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
                    if (!args[index].equalsIgnoreCase("off"))
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
                    if (args.length-index < 1)
                    {
                        return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                                "Use ``"+head+" "+args[0]+" "+args[index-1]+" [\"title\"]``";
                    }
                    if (args[index].length() > 255)
                    {
                        return "Your title can be at most 255 characters!";
                    }
                    index++;
                    break;

                case "d":
                case "date":
                case "sd":
                case "start-date":
                    verify = VerifyUtilities.verifyDate(args, index, head, entry, zone, true);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;


                case "ed":
                case "end-date":
                    verify = VerifyUtilities.verifyDate(args, index, head, entry, zone, false);
                    if(!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "r":
                case "repeats":
                case "repeat":
                    verify = VerifyUtilities.verifyRepeat(args, index, head);
                    if (!verify.isEmpty()) return verify;
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

                case "co":
                case "count":
                    verify = VerifyUtilities.verifyCount(args, index, head);
                    if (!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "lo":
                case "location":
                    verify = VerifyUtilities.verifyLocation(args, index, head);
                    if (!verify.isEmpty()) return verify;
                    index++;
                    break;

                case "an":
                case "announce":
                case "announcement":
                case "announcements":
                    if (args.length - index < 1)
                    {
                        return "That's not the right number of arguments for **" + args[index - 1] + "**!\n" +
                                "Use ``" + head + " " + args[0] + " " + args[index - 1] + " [add|remove] [#target] [time] [message]``";
                    }
                    switch(args[index++].toLowerCase())
                    {
                        case "a":
                        case "add":
                            verify = VerifyUtilities.verifyAnnouncementAdd(args, index, head, event);
                            if(!verify.isEmpty()) return verify;
                            index += 3;
                            break;

                        case "r":
                        case "remove":
                            verify = VerifyUtilities.verifyAnnouncementRemove(args, index, head, entry);
                            if(!verify.isEmpty()) return verify;
                            index ++;
                            break;

                        default:
                            return "**" + args[index-1] + "** is not a valid option!\n" +
                                    "You should use either *add* or *remove*!";
                    }
                    break;

                case "a":
                case "add":
                    if (args.length-index < 1)
                    {
                        return "That's not the right number of arguments!\n" +
                                "Use ``" + head + " " + args[0] + " " + args[index-1] + " [announcement|comment] [args]";
                    }
                    switch(args[index++].toLowerCase())
                    {
                        case "a":
                        case "an":
                        case "announce":
                        case "announcement":
                        case "announcements":
                            verify = VerifyUtilities.verifyAnnouncementAdd(args, index, head, event);
                            if(!verify.isEmpty()) return verify;
                            index += 3;
                            break;

                        case "c":
                        case "comment":
                        case "comments":
                            verify = VerifyUtilities.verifyCommentAdd(args, index, head);
                            if(!verify.isEmpty()) return verify;
                            index++;
                            break;

                        default:
                            return "*" + args[index-1] + "* is not a valid option!\n" +
                                    "Please use either *comment* or **announcement*!";
                    }
                    break;

                case "re":
                case "remove":
                    if (args.length-index < 1)
                    {
                        return "That's not the right number of arguments!\n" +
                                "Use ``" + head + " " + args[0] + " " + args[index-1] + " [announcement|comment] [args]";
                    }
                    switch(args[index++].toLowerCase())
                    {
                        case "a":
                        case "an":
                        case "announce":
                        case "announcement":
                        case "announcements":
                            verify = VerifyUtilities.verifyAnnouncementRemove(args, index, head, entry);
                            if(!verify.isEmpty()) return verify;
                            index++;
                            break;

                        case "c":
                        case "comment":
                        case "comments":
                            verify = VerifyUtilities.verifyCommentRemove(args, index, head, entry);
                            if(!verify.isEmpty()) return verify;
                            index++;
                            break;

                        default:
                            return "*" + args[index-1] + "* is not a valid option!\n" +
                                    "Please use either *comment* or **announcement*!";
                    }
                    break;

                case "color":
                    verify = VerifyUtilities.verifyColor(args, index, head);
                    if(!verify.isEmpty()) return verify;
                    index++;
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

        Integer entryId  = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry se = Main.getEntryManager().getEntry( entryId );

        Message msg = se.getMessageObject();
        if (msg==null) return;

        //
        // edit the event if command contains more arguments than the event ID,
        // otherwise skip this and print out the event configuration
        if(args.length > 1)
        {
            index++;    // 1
            boolean limitsChanged = false;
            while(index < args.length)
            {
                ZoneId zone = Main.getScheduleManager().getTimeZone(se.getChannelId());
                ArrayList<String> originalComments = se.getComments();
                ArrayList<String> comments;
                switch (args[index++].toLowerCase())
                {
                    case "c":
                    case "comment":
                    case "comments":
                        comments = se.getComments();
                        switch(args[index++])
                        {
                            case "a":
                            case "add" :
                                comments.add(args[index]);
                                se.setComments(comments);
                                index++;
                                break;
                            case "r":
                            case "remove" :
                                if(VerifyUtilities.verifyInteger(args[index]))
                                {
                                    String comment = originalComments.get(Integer.parseInt(args[index])-1);
                                    comments.remove(comment);
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
                                int i1 = Integer.parseInt(args[index])-1;
                                int i2 = Integer.parseInt(args[index+1])-1;
                                if (i1 < comments.size() && i2 < comments.size())
                                {
                                    String a = comments.get(i1);
                                    String b = comments.get(i2);
                                    comments.set(i1, b);
                                    comments.set(i2, a);
                                    se.setComments(comments);
                                }
                                index += 2;
                                break;
                            case "m":
                            case "modify":
                            case "replace":
                            case "set":
                                int no = Integer.parseInt(args[index])-1;
                                comments.set(no, args[index+1]);

                                se.setComments(comments);
                                index += 2;
                                break;
                        }
                        break;

                    case "desc":
                    case "description":
                        String description =
                                args[index].toLowerCase().matches("(off)|(default)") ? "%g" : args[index];
                        se.setDescription(description);
                        index++;
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
                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();
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
                        {   // otherwise parse the input for a time
                            se.setEnd(ZonedDateTime.of(se.getEnd().toLocalDate(),
                                        ParsingUtilities.parseTime(args[index]),
                                        se.getEnd().getZone()));
                        }

                        // add a day if the time has already passed
                        if(ZonedDateTime.now().isAfter(se.getEnd())) se.setEnd(se.getEnd().plusDays(1));

                        // add a day to end if end is after start
                        if(se.getStart().isAfter(se.getEnd())) se.setEnd(se.getEnd().plusDays(1));

                        // reload end reminders
                        se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();
                        index++;
                        break;

                    case "t":
                    case "title":
                        se.setTitle(args[index]);
                        index++;
                        break;

                    case "d":
                    case "date":
                        LocalDate date = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setStart(ZonedDateTime.of(date, se.getStart().toLocalTime(), zone));
                        se.setEnd(ZonedDateTime.of(date, se.getEnd().toLocalTime(), zone));

                        // update the reminders and announcements to appropriate datetimes
                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                                .reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();
                        index++;
                        break;

                    case "sd":
                    case "start date":
                    case "start-date":
                        LocalDate sdate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setStart(ZonedDateTime.of(sdate, se.getStart().toLocalTime(), zone));

                        // change end date to a valid datetime if necessary
                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setEnd(se.getStart());
                            se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()));
                        }

                        // update the reminders and announcements to appropriate datetimes
                        se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();
                        index++;
                        break;

                    case "ed":
                    case "end date":
                    case "end-date":
                        LocalDate edate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                        se.setEnd(ZonedDateTime.of(edate, se.getEnd().toLocalTime(), zone));

                        // change start date to a valid datetime if necessary
                        if(se.getEnd().isBefore(se.getStart()))
                        {
                            se.setStart(se.getEnd());
                            se.reloadReminders(Main.getScheduleManager().getReminders(se.getChannelId()));
                        }

                        // update the reminders and announcements to appropriate datetimes
                        se.reloadEndReminders(Main.getScheduleManager().getEndReminders(se.getChannelId()))
                                .regenerateAnnouncementOverrides();
                        index++;
                        break;

                    case "r":
                    case "repeats":
                    case "repeat":
                        se.setRepeat(EventRecurrence.parseRepeat(args[index].toLowerCase()));
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
                        LocalDate expireDate = ParsingUtilities.parseNullableDate(args[index], zone);
                        se.setExpire(expireDate == null ?
                                null : ZonedDateTime.of(expireDate, LocalTime.MAX, zone));
                        index++;
                        break;

                    case "deadline":
                    case "dl":
                        LocalDate deadlineDate = ParsingUtilities.parseNullableDate(args[index], zone);
                        se.setRsvpDeadline(deadlineDate == null ?
                                null : ZonedDateTime.of(deadlineDate, LocalTime.MAX, zone));
                        index++;
                        break;

                    case "count":
                    case "co":
                        Integer c = null;
                        if (!args[index].equalsIgnoreCase("off"))
                            c = Integer.parseInt(args[index]);
                        se.setCount(c);
                        se.setOriginalStart(se.getStart());    // set the original start to the current start
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
                        if (se.isQuietRemind()
                                && se.isQuietEnd()
                                && se.isQuietStart())
                            se.setQuietRemind(false).setQuietEnd(false).setQuietStart(false);
                        else
                            se.setQuietRemind(true).setQuietEnd(true).setQuietStart(true);
                        break;

                    case "limit":
                    case "l":
                        Integer lim = null;
                        if (!args[index+1].equalsIgnoreCase("off"))
                            lim = Integer.parseInt(args[index+1]);
                        se.setRsvpLimit(args[index], lim);
                        limitsChanged = true;
                        index += 2;
                        break;

                    case "lo":
                    case "location":
                        if (args[index].equalsIgnoreCase("off"))
                            se.setLocation(null);
                        else
                            se.setLocation(args[index]);
                        index++;
                        break;

                    case "an":
                    case "announce":
                    case "announcement":
                    case "announcements":
                        switch (args[index++].toLowerCase())
                        {
                            case "a":
                            case "add":
                                String target = args[index].replaceAll("[^\\d]","");
                                String time = args[index+1];
                                String message = args[index+2];
                                se.addAnnouncementOverride(target, time, message);
                                index += 3;
                                break;

                            case "r":
                            case "remove":
                                Integer id = Integer.parseInt(args[index].replaceAll("[^\\d]",""))-1;
                                se.removeAnnouncementOverride(id);
                                index++;
                                break;
                        }
                        break;

                    case "a":
                    case "add":
                        switch (args[index++].toLowerCase())
                        {
                            case "a":
                            case "an":
                            case "announce":
                            case "announcement":
                            case "announcements":
                                String target = args[index].replaceAll("[^\\d]","");
                                String time = args[index+1];
                                String message = args[index+2];
                                se.addAnnouncementOverride(target, time, message);
                                index += 3;
                                break;

                            case "c":
                            case "comment":
                            case "comments":
                                comments = se.getComments();
                                comments.add( args[index] );
                                se.setComments(comments);
                                index++;
                                break;
                        }
                        break;

                    case "re":
                    case "remove":
                        switch (args[index++].toLowerCase())
                        {
                            case "a":
                            case "an":
                            case "announce":
                            case "announcement":
                            case "announcements":
                                Integer id = Integer.parseInt(args[index].replaceAll("[^\\d]",""))-1;
                                se.removeAnnouncementOverride(id);
                                index++;
                                break;

                            case "c":
                            case "comment":
                            case "comments":
                                comments = se.getComments();
                                if(VerifyUtilities.verifyInteger(args[index]))
                                    comments.remove(Integer.parseInt(args[index])-1);
                                else
                                    comments.remove(args[index]);
                                se.setComments(comments);
                                index++;
                                break;
                        }
                        break;

                    case "color":
                        if (args[index].toLowerCase().matches("off||null||none"))
                            se.setColor(null);
                        else
                            se.setColor(args[index]);
                        index++;
                        break;
                }
            }
            Main.getEntryManager().updateEntry(se, true);
            if (limitsChanged) // if the limits on the event was changed, reload the reactions
            {
                se.getMessageObject().clearReactions().queue(message->
                {
                    Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
                    String clearEmoji = Main.getScheduleManager().getRSVPClear(se.getChannelId());
                    EntryManager.addRSVPReactions(options, clearEmoji, se.getMessageObject(), se);
                }, failure-> Logging.exception(this.getClass(), failure));
            }
        }


        //
        // send the event summary to the command channel
        //
        String body = "Updated event :id: **"+ ParsingUtilities.intToEncodedID(se.getId()) +"** on <#" +
                se.getChannelId() + ">\n" + se.toString();
        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
