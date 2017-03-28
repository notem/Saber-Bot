package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

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
    private String prefix = Main.getBotSettingsManager().getCommandPrefix();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``" + prefix + "create <channel> <title> <start> [<end> <extra>]`` will add a" +
                " new entry to a schedule. Entries MUST be initialized with a title, and a start " +
                "time. The end time (<end>) may be omitted. Start and end times should be of form h:mm with " +
                "optional am/pm appended on the end." +
                "\n\n" +
                "Entries can optionally be configured with comments, repeat, and a start date. \nAdding ``repeat " +
                "<no|daily|\"Su,Mo,Tu,We,Th,Fr,Sa\">`` to ``<extra>`` will configure the event to repeat with the " +
                "given interval; default behavior is no repeat. \nAdding ``date MM/dd`` to " +
                "``<extra>`` will set the events start date; default behavior is to use the current date or the " +
                "next day depending on if the current time is greater than the start time. \nComments may be added by" +
                " adding ``\"YOUR COMMENT\"`` in ``<extra>``; any number of comments may be added in ``<extra>``." +
                "\n\n" +
                "If your title, comment, or channel includes any space characters, the phrase my be enclosed in " +
                "quotations (see examples).";

        String EXAMPLES = "" +
                "Ex1. ``!create #event_schedule \"Party in the Guild Hall\" 19:00 02:00``" +
                "\nEx2. ``!create \"#guild_reminders\" \"Sign up for Raids\" 4:00pm 4:00pm``" +
                "\nEx3. ``!create \"#raid_schedule\" \"Weekly Raid Event\" 7:00pm 12:00pm repeat weekly \"Healers and " +
                "tanks always in demand.\" \"PM our raid captain with your role and level if attending.\"``";

        String USAGE_BRIEF = "``" + prefix + "create`` - add an event to a schedule";

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
            return "That's not enough arguments!";

        if( !Main.getScheduleManager().isASchedule(args[index].replace("<#","").replace(">","")) )
            return "Channel " + args[index] + " is not schedule for your guild. " +
                    "You can use the ``init`` command to create a new schedule.";

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

            for (String arg : argsRemaining)
            {
                if (dateFlag)
                {
                    if (!VerifyUtilities.verifyDate(arg))
                        return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                    dateFlag = false;
                }
                else if (urlFlag)
                {
                    if (!VerifyUtilities.verifyUrl(arg))
                        return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                    urlFlag = false;
                }
                else if (arg.equals("date"))
                {
                    dateFlag = true;
                }
                else if (arg.equals("url"))
                {
                    urlFlag = true;
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
        String eTitle = "";

        LocalTime eStart = LocalTime.now().plusMinutes(1);    //
        LocalTime eEnd = null;                  //
        ArrayList<String> eComments = new ArrayList<>();      // defaults initialized
        int repeat = 0;                                       //
        LocalDate eDate = LocalDate.now();                    //
        String url = null;
        String cId = args[0].replace("<#","").replace(">","");

        boolean channelFlag = false;  // true if the channel name arg has been grabbed
        boolean titleFlag = false;    // true if eTitle has been grabbed from args
        boolean startFlag = false;    // true if eStart has been grabbed from args
        boolean endFlag = false;      // true if eEnd has been grabbed from args
        boolean repeatFlag = false;   // true if a 'repeat' arg has been grabbed
        boolean dateFlag = false;
        boolean urlFlag = false;

        for( String arg : args )
        {
            if(!channelFlag)
            {
                channelFlag = true;
            }
            else if(!titleFlag)
            {
                titleFlag = true;
                eTitle = arg;
            }
            else if(!startFlag)
            {
                startFlag = true;
                eStart = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else if(!endFlag && VerifyUtilities.verifyTime(arg))
            {
                endFlag = true;
                eEnd = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else
            {
                if (!endFlag)
                    endFlag = true;

                if (repeatFlag)
                {
                    repeat = ParsingUtilities.parseWeeklyRepeat(arg.toLowerCase());
                    repeatFlag = false;
                }
                else if (dateFlag)
                {
                    eDate = ParsingUtilities.parseDateStr(arg.toLowerCase());
                    dateFlag = false;
                }
                else if (urlFlag)
                {
                    url = arg;
                    urlFlag = false;
                }
                else if(arg.toLowerCase().equals("repeats") ||
                        arg.toLowerCase().equals("repeat"))
                {
                    repeatFlag = true;
                }
                else if (arg.toLowerCase().equals("date"))
                {
                    dateFlag = true;
                }
                else if (arg.toLowerCase().equals("url"))
                {
                    urlFlag = true;
                }
                else
                {
                    eComments.add(arg);
                }
            }
        }

        if(eEnd == null)
        {
            eEnd = LocalTime.from(eStart);
        }

        ZonedDateTime s = ZonedDateTime.of( eDate, eStart, Main.getScheduleManager().getTimeZone(cId) );
        ZonedDateTime e = ZonedDateTime.of( eDate, eEnd, Main.getScheduleManager().getTimeZone(cId) );

        if(ZonedDateTime.now().isAfter(s)) //add a day if the time has already passed
        {
            s = s.plusDays(1);
            e = e.plusDays(1);
        }

        if(s.isAfter(e))        //add a day to end if end is after start
            e = e.plusDays(1);

        Main.getEntryManager().newEntry(eTitle, s, e, eComments, repeat, url,
                event.getGuild().getTextChannelById(cId));
    }
}
