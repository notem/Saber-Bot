package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * CreateCommand places a new entry message on the discord schedule channel
 * a ScheduleEntry is not created until the message sent by this command is parsed by
 * the listener
 */
public class CreateCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "create";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``" + invoke + " <channel> <title> <start> [<end> <extra>]`` will add a" +
                " new entry to a schedule. Entries MUST be initialized with a title, and a start " +
                "time. The end time (<end>) may be omitted. Start and end times should be of form h:mm with " +
                "optional am/pm appended on the end." +
                "\n\n" +
                "Optionally, events can be configured with comments, repeat settings, and a start/end dates." +
                "\n\n" +
                "Repeat settings can be configured by adding ``repeat <daily|\"Su,Mo,Tu,We,Th,Fr,Sa\">`` to ``<extra>``" +
                " to cause the event to repeat on the given days. Default behavior is no repeat. An event can instead " +
                "be configured to repeat on a daily interval by adding ``interval <number>`` to ``<extra>``" +
                "\n\n" +
                "Adding ``date <MM/dd>`` to ``<extra>`` will set the event's start and end date. For more granular " +
                "control you can instead use ``start-date <MM/dd>`` and ``end-date <MM/dd>`` in place of ``date``." +
                "Default behavior is to use the current date or the " +
                "next day depending on if the current time is greater than the start time." +
                "\n\n" +
                "Comments may be added by adding ``\"YOUR COMMENT\"`` at the end of ``<extra>``. " +
                "Up to 10 of comments may be added in ``<extra>``." +
                "\n\n" +
                "If your title, comment, or channel includes any space characters, the phrase must be enclosed in " +
                "quotations (see examples).";

        String EXAMPLES = "" +
                "Ex1. ``" + invoke + " #event_schedule \"Party in the Guild Hall\" 19:00 02:00``" +
                "\nEx2. ``" + invoke + " #guild_reminders \"Sign up for Raids\" 4:00pm interval 2``" +
                "\nEx3. ``" + invoke + " #raid_schedule \"Weekly Raid Event\" 7:00pm 12:00pm repeat weekly \"Healers and " +
                "tanks always in demand.\" \"PM our raid captain with your role and level if attending.\"``" +
                "\nEx4. ``" + invoke + " #competition \"Capture the Flag\" 10:00am start-date 10/20 end-date 10/23``";

        String USAGE_BRIEF = "``" + invoke + "`` - add an event to a schedule";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }
    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 3)
            return "That's not enough arguments! Use ``" + invoke + " <channel> <title> <start> [<end> <extra>]``";

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
            return "Channel " + args[index] + " is not a schedule for your guild. " +
                    "You can use the ``init`` command to create a new schedule.";

        if( Main.getScheduleManager().isLocked(cId) )
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes. " +
                    "(If this does not go away ping @notem in the support server)";

        index++;

        // check <title>
        if( args[index].length() > 255 )
            return "Your title can be at most 255 characters!";

        index++;

        // check <start>
        if( !VerifyUtilities.verifyTime( args[index] ) )
            return "I could not understand **" + args[index] + "** as a time! Please use the format hh:mm[am|pm].";

        // if minimum args, then ok
        if (args.length == 3)
            return "";

        index++;

        // if <end> fails verification, assume <end> has been omitted
        if( VerifyUtilities.verifyTime( args[index] ) )
            index++;

        // check remaining args
        if( args.length - 1 > index )
        {
            String[] argsRemaining = Arrays.copyOfRange(args, index, args.length);

            boolean dateFlag = false;
            boolean urlFlag = false;
            boolean intervalFlag = false;

            for (String arg : argsRemaining)
            {
                if (dateFlag)
                {
                    if (!VerifyUtilities.verifyDate(arg))
                        return "I could not understand **" + arg + "** as a date! Please use the format M/d.";
                    dateFlag = false;
                }
                else if (urlFlag)
                {
                    if (!VerifyUtilities.verifyUrl(arg))
                        return "**" + arg + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                    urlFlag = false;
                }
                else if (intervalFlag)
                {
                    if (!VerifyUtilities.verifyInteger(arg))
                        return "**" + arg + "** is not a number!";
                    if(Integer.parseInt(arg) < 1)
                        return "Your repeat interval can't be negative!";
                    intervalFlag = false;
                }
                else
                {
                    switch(arg.toLowerCase())
                    {
                        case "d":
                        case "sd":
                        case "ed":
                        case "date":
                        case "end date":
                        case "start date":
                        case "end-date":
                        case "start-date":
                            dateFlag = true;
                            break;

                        case "u":
                        case "url":
                            urlFlag = true;
                            break;

                        case "i":
                        case "interval":
                            intervalFlag = true;
                            break;
                    }
                }
            }
        }

        if (Main.getEntryManager().isLimitReached(event.getGuild().getId()))
        {
            return "I can't allow your guild any more entries."
                    + "Please remove some entries before trying again.";
        }

        return ""; // return valid
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // Initialize variables with safe defaults
        String title = "";
        LocalTime startTime = LocalTime.now().plusMinutes(1);
        LocalTime endTime = null;
        ArrayList<String> comments = new ArrayList<>();
        int repeat = 0;
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = null;
        String url = null;

        String cId = args[0].replace("<#","").replace(">","");

        boolean channelFlag = false;
        boolean titleFlag = false;
        boolean startFlag = false;
        boolean endFlag = false;
        boolean repeatFlag = false;
        boolean dateFlag = false;
        boolean startDateFlag = false;
        boolean endDateFlag = false;
        boolean urlFlag = false;
        boolean intervalFlag = false;

        for( String arg : args )
        {
            if(!channelFlag)
            {
                channelFlag = true;
            }
            else if(!titleFlag)
            {
                titleFlag = true;
                title = arg;
            }
            else if(!startFlag)
            {
                startFlag = true;
                startTime = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else if(!endFlag && VerifyUtilities.verifyTime(arg))
            {
                endFlag = true;
                endTime = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else
            {
                if (!endFlag) // skip end if not provided at this point
                {
                    endFlag = true;
                }
                if (repeatFlag)
                {
                    repeat = ParsingUtilities.parseWeeklyRepeat(arg.toLowerCase());
                    repeatFlag = false;
                }
                else if (dateFlag)
                {
                    startDate = ParsingUtilities.parseDateStr(arg.toLowerCase());
                    endDate = startDate;
                    dateFlag = false;
                }
                else if (startDateFlag)
                {
                    startDate = ParsingUtilities.parseDateStr(arg.toLowerCase());
                    startDateFlag = false;
                }
                else if (endDateFlag)
                {
                    endDate = ParsingUtilities.parseDateStr(arg.toLowerCase());
                    endDateFlag = false;
                }
                else if (urlFlag)
                {
                    url = arg;
                    urlFlag = false;
                }
                else if (intervalFlag)
                {
                    repeat = 0b10000000 | Integer.parseInt(arg);
                    intervalFlag = false;
                }
                else
                {
                    switch(arg.toLowerCase())
                    {
                        case "r":
                        case "repeat":
                        case "repeats":
                            repeatFlag = true;
                            break;

                        case "d":
                        case "date":
                            dateFlag = true;
                            break;

                        case "ed":
                        case "end date":
                        case "end-date":
                            endDateFlag = true;
                            break;

                        case "sd":
                        case "start date":
                        case "start-date":
                            startDateFlag = true;
                            break;

                        case "u":
                            urlFlag = true;
                            break;

                        case "i":
                        case "interval":
                            intervalFlag = true;
                            break;

                        default:
                            comments.add(arg);
                            break;
                    }
                }
            }
        }

        // if the end time has not been filled, copy start time
        if(endTime == null)
        {
            endTime = LocalTime.from(startTime);
        }

        // if the end date has not been filled, copy start date
        if(endDate == null)
        {
            endDate = LocalDate.from(startDate);
        }

        // create the zoned date time using the schedule's timezone
        ZonedDateTime start = ZonedDateTime.of( startDate, startTime, Main.getScheduleManager().getTimeZone(cId) );
        ZonedDateTime end = ZonedDateTime.of( endDate, endTime, Main.getScheduleManager().getTimeZone(cId) );

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

        Main.getEntryManager().newEntry(title, start, end, comments, repeat, url,
                event.getGuild().getTextChannelById(cId), null);
        Main.getScheduleManager().sortSchedule(cId);
    }
}
