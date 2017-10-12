package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * CreateCommand places a new entry message on the discord schedule channel
 * a ScheduleEntry is not created until the message sent by this command is parsed by
 * the listener
 */
public class CreateCommand implements Command
{
    @Override
    public String name()
    {
        return "create";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String cmd = prefix + this.name();
        String usage = "``"+cmd+"`` - add an event to a schedule";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.CORE);

        String cat1 = "- Usage\n"+cmd+ " <channel> <title> <start> [<end> <extra>]";
        String cont1 = "The create command will add a new entry to a schedule.\n" +
                        "Entries MUST be initialized with a title, and a start time." +
                        "\n\n" +
                        "The end time (<end>) may be omitted. Start and end times should be of form h:mm with " +
                        "optional am/pm appended on the end.\n" +
                        "Optionally, events can be configured with comments, repeat settings, and a start/end dates.";
        info.addUsageCategory(cat1, cont1);

        String cat2 = "+ Repeat on weekdays or interval";
        String cont2 = "Repeat settings can be configured by adding ``repeat <daily|\"Su,Mo,Tu,We,Th,Fr,Sa\">`` to ``<extra>``" +
                        " to cause the event to repeat on the given days.\n Default behavior is no repeat.\n An event can instead " +
                        "be configured to repeat on a daily interval by adding ``interval <number>`` to ``<extra>``";

        info.addUsageCategory(cat2, cont2);

        String cat3 = "+ Start and end date";
        String cont3 = "Adding ``date <yyyy/MM/dd>`` to ``<extra>`` will set the event's start and end date.\n For more granular " +
                        "control you can instead use ``start-date <yyyy/MM/dd>`` and ``end-date <yyyy/MM/dd>`` in place of ``date``." +
                        "\n\n" +
                        "The date must be formatted like year/month/day, however year and month can be omitted " +
                        "('month/day' and 'day' are valid).\nThe omitted values will be inherited from the current date." +
                        "\n\n" +
                        "Dates which are in a non-number format (such as '10 May') are not acceptable.\n" +
                        "Default behavior is to use the next day as the event's date." +
                        "\n\nAs a shortcut, appending ``today`` to the command will act like the ``date M/d`` option where " +
                        "the date is set to the current day.";
        info.addUsageCategory(cat3, cont3);

        String cat4 = "+ Event description";
        String cont4 = "Comments may be added by adding ``\"YOUR COMMENT\"`` at the end of ``<extra>``.\n" +
                        "Up to 10 of comments may be added in ``<extra>``.\n" +
                        "If your title, comment, or channel includes any space characters, the phrase must be enclosed in " +
                        "quotations (see examples).";
        info.addUsageCategory(cat4, cont4);

        String cat5 = "+ Event expiration";
        String cont5 = "In some instances of a repeating event, it may be desirable to set a date for when that event will stop recurring.\n" +
                        "This can be accomplished using the ``expire`` argument.\n" +
                        "Add ``expire <yyyy/MM/dd>`` to a create command to create an event with an expiration date (see examples).";
        info.addUsageCategory(cat5, cont5);

        info.addUsageExample(cmd+" #event_schedule \"Party in the Guild Hall\" 19:00 2:00");
        info.addUsageExample(cmd+" #guild_reminders \"Sign up for Raids\" 4:00pm interval 2");
        info.addUsageExample(cmd+" #raid_schedule \"Weekly Raid Event\" 7:00pm 12:00pm repeat \"Fri, Sun\" \"Healers and " +
                "tanks always in demand.\" \"PM our raid captain with your role and level if attending.\"");
        info.addUsageExample(cmd+" #competition \"Capture the Flag\" 10:00am start-date 10/20 end-date 10/23");
        info.addUsageExample(cmd+" #shows \"Pokemon\" 5:29pm 6:00pm date 3/30 repeat \"Sat\" expire 5/30");

        return info;
    }


    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        // arg count check
        if (args.length < 3)
        {
            return "That's not enough arguments!\n" +
                    "Use ``" + head + " <channel> <title> <start> [<end> <extra>]``";
        }

        // schedule check
        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
        {
            return "Channel " + args[index] + " is not a schedule for your guild. " +
                    "Use the ``" + prefix + "init`` command to create a new schedule!";
        }
        if( Main.getScheduleManager().isLocked(cId) )
        {
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes.";
        }
        index++; // 1

        // check <title>
        if (args[index].length() > 255)
        {
            return "Your title can be at most 255 characters!";
        }
        index++; // 2

        // check <start>
        if (!VerifyUtilities.verifyTime(args[index]))
        {
            return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";
        }
        ZoneId zone = Main.getScheduleManager().getTimeZone(cId);
        ZonedDateTime startTime = ZonedDateTime.of(LocalDate.now().plusDays(1), ParsingUtilities.parseTime(args[index]), zone);

        // if minimum args, then ok
        if (args.length == 3) return "";
        index++; // 3

        // if <end> fails verification, assume <end> has been omitted
        if (VerifyUtilities.verifyTime(args[index]))
        {
            index++; // 4
        }

        // check remaining args
        if (args.length - 1 > index)
        {
            args = Arrays.copyOfRange(args, index, args.length);
            index = 0;
            while (index < args.length)
            {
                String verify;
                switch (args[index++].toLowerCase())
                {
                    case "d":
                    case "date":
                    case "sd":
                    case "start-date":
                    case "ed":
                    case "end-date":
                        verify = VerifyUtilities.verifyDate(args, index, head, null, zone);
                        if (!verify.isEmpty()) return verify;
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
                        if (!verify.isEmpty()) return verify;
                        index++;
                        break;

                    case "ex":
                    case "expire":
                        verify = VerifyUtilities.verifyExpire(args, index, head, zone);
                        if (!verify.isEmpty()) return verify;
                        index++;
                        break;

                    case "limit":
                    case "l":
                        verify = VerifyUtilities.verifyLimit(args, index, head, null);
                        if (!verify.isEmpty()) return verify;
                        index++;
                        break;

                    case "deadline":
                    case "dl":
                        verify = VerifyUtilities.verifyDeadline(args, index, head);
                        if (!verify.isEmpty()) return verify;
                        index++;
                        break;

                    case "today":
                        if(startTime.isBefore(ZonedDateTime.now()))
                        {
                            return "I cannot be schedule the event for today as the start time is in the past!";
                        }
                        break;

                    default:
                        if(args[index-1].length() > 1024) return "Comments should not be larger than 1024 characters!";
                        break;
                }
            }
        }

        // verify that guild has not exceeded maximum entries
        if (Main.getEntryManager().isLimitReached(event.getGuild().getId()))
        {
            return "I can't allow your guild any more entries."
                    + "Please remove some entries before trying again.";
        }

        return ""; // return valid
    }


    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        // get schedule ID and zone information
        String cId = args[index++].replace("<#","").replace(">","");
        ZoneId zone = Main.getScheduleManager().getTimeZone(cId);

        // Initialize variables
        String title;
        LocalTime startTime;
        LocalTime endTime = null;
        ArrayList<String> comments = new ArrayList<>();
        int repeat = 0;
        ZonedDateTime startDate = ZonedDateTime.now(zone).plusDays(1);
        ZonedDateTime endDate = null;
        String url = null;
        String image = null;
        String thumbnail = null;
        ZonedDateTime expire = null;
        ZonedDateTime deadline = null;

        // process title
        title = args[index];
        index++;

        // process start
        startTime = ParsingUtilities.parseTime(args[index].trim().toUpperCase());
        index++;

        // if minimum args, then ok
        if (args.length != 3)
        {
            // if <end> fails verification, assume <end> has been omitted
            if(VerifyUtilities.verifyTime(args[index]))
            {
                endTime = ParsingUtilities.parseTime(args[index].trim().toUpperCase());
                index++;
            }

            // check remaining args
            if(args.length > index)
            {
                args = Arrays.copyOfRange(args, index, args.length);
                index = 0;
                while(index < args.length)
                {
                    switch(args[index++].toLowerCase())
                    {
                        case "d":
                        case "date":
                            startDate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                            endDate = startDate;
                            index++;
                            break;

                        case "sd":
                        case "start date":
                        case "start-date":
                            startDate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                            index++;
                            break;

                        case "ed":
                        case "end date":
                        case "end-date":
                            endDate = ParsingUtilities.parseDate(args[index].toLowerCase(), zone);
                            index++;
                            break;

                        case "r":
                        case "repeats":
                        case "repeat":
                            repeat = ParsingUtilities.parseRepeat(args[index].toLowerCase());
                            index++;
                            break;

                        case "i":
                        case "interval":
                            repeat = ParsingUtilities.parseInterval(args[index]);
                            index++;
                            break;

                        case "u":
                        case "url":
                            url = ParsingUtilities.parseUrl(args[index]);
                            index++;
                            break;

                        case "ex":
                        case "expire":
                            expire = ParsingUtilities.parseNullableDate(args[index], zone);
                            index++;
                            break;

                        case "im":
                        case "image":
                            image = ParsingUtilities.parseUrl(args[index]);
                            index++;
                            break;

                        case "th":
                        case "thumbnail":
                            thumbnail = ParsingUtilities.parseUrl(args[index]);
                            index++;
                            break;

                        case "deadline":
                        case "dl":
                            deadline = ParsingUtilities.parseNullableDate(args[index], zone);
                            index++;
                            break;

                        case "today":
                            startDate = ZonedDateTime.now(zone);
                            endDate = ZonedDateTime.now(zone);
                            break;

                        case "tomorrow":
                            startDate = ZonedDateTime.now(zone).plusDays(1);
                            endDate = ZonedDateTime.now(zone).plusDays(1);
                            break;

                        case "overmorrow":
                            startDate = ZonedDateTime.now(zone).plusDays(2);
                            endDate = ZonedDateTime.now(zone).plusDays(2);
                            break;

                        default:
                            comments.add(args[index-1]);
                            break;
                    }
                }
            }
        }

        /*
         * Finalize the start and end times from
         * the user-provided arguments
         */

        // handle all day events
        boolean a = startTime.equals(endTime) && startTime.equals(LocalTime.MIN); // is all day event
        boolean b = startTime.equals(LocalTime.MAX) && endDate == null;           // another way to define all-day events?
        if(a || b)
        {
            endDate = ZonedDateTime.from(startDate).plusDays(1);
        }
        // if the end time has not been filled, copy start time
        if(endTime == null)
        {
            endTime = LocalTime.from(startTime);
        }
        // if the end date has not been filled, copy start date
        if(endDate == null)
        {
            endDate = ZonedDateTime.from(startDate);
        }

        // create the zoned date time using the schedule's timezone
        ZonedDateTime start = ZonedDateTime.of(startDate.toLocalDate(), startTime, zone);
        ZonedDateTime end = ZonedDateTime.of(endDate.toLocalDate(), endTime, zone);

        // add a year to the date if the provided date is past current time
        Instant now = Instant.now();
        if(now.isAfter(start.toInstant()))
        {
            start = start.plusYears(1);
        }
        if(now.isAfter(end.toInstant()))
        {
            end = end.plusYears(1);
        }

        // add a day to end if end is after start
        if(start.isAfter(end))
        {
            end = start.plusDays(1);
        }

        /*
         * Create a dummy schedule entry with all processed variables
         * and create the new event via EntryManager
         */

        // create the dummy schedule entry
        ScheduleEntry se = (new ScheduleEntry(event.getGuild().getTextChannelById(cId), title, start, end))
                .setComments(comments)
                .setRepeat(repeat)
                .setTitleUrl(url)
                .setExpire(expire)
                .setImageUrl(image)
                .setThumbnailUrl(thumbnail)
                .setRsvpDeadline(deadline);

        // finalize the schedule entry
        Integer entryId = Main.getEntryManager().newEntry(se, true);

        // send the event summary to the command channel
        String body = "New event created :id: **"+ ParsingUtilities.intToEncodedID(entryId) +"** on <#" + cId + ">\n" +
                "```js\n" + se.toString() + "\n```";
        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
