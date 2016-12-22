package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.ScheduleEntry;
import io.schedulerbot.core.ScheduleEntryParser;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.ParsingUtilities;
import io.schedulerbot.utils.VerifyUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private static String prefix = Main.getSettings().getCommandPrefix();
    private static int maxEntries = Main.getSettings().getMaxEntries();
    private static String scheduleChan = Main.getSettings().getScheduleChan();

    private static final String USAGE_EXTENDED = "Event entries can be initialized using the form **" + prefix +
            "create \"TITLE\" <Start> <End> <Optional>**. Entries MUST be initialized with a title, a start " +
            "time, and an end time. Start and end times should be of form h:mm with optional am/pm appended on " +
            "the end. Entries can optionally be " +
            "configured with comments, repeat, and a start date. Adding **repeat no**/**daily**/**weekly** to " +
            "**<Optional>** will configure repeat; default behavior is no repeat. Adding **date MM/dd** to " +
            "**<Optional>** will configure the start date; default behavior is to use the current date or the " +
            "next day depending on if the current time is greater than the start time. Comments may be added by" +
            " adding **\"YOUR COMMENT\"** in **<Optional>**; any number of comments may be added in **<Optional>" +
            "**.";

    private static final String EXAMPLES = "Ex1. **!create \"Party in the Guild Hall\" 19:00 02:00**" +
            "\nEx2. **!create \"Weekly Raid Event\" 7:00pm 12:00pm repeat weekly \"Healers and tanks always in " +
            "demand.\" \"PM our raid captain with your role and level if attending.\"**";

    private static final String USAGE_BRIEF = "**" + prefix + "create** - Generates a new event entry" +
            " in #" + scheduleChan + ".";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        if( args.length < 3 )
            return false;

        // check title
        int index = 0;
        if( !(args[index].startsWith("\"") && args[index].endsWith("\"")) )
        {
            for (index = 1; index < args.length - 1; index++)
                if (args[index].endsWith("\""))
                    break;

            if( !VerifyUtilities.verifyString( Arrays.copyOfRange( args, 0, index+1 )))
                return false;
        }

        // check that there are enough args remaining for start and end
        if( args.length - 1  < index + 2 )
            return false;

        // check start
        if( !VerifyUtilities.verifyTime( args[index+1] ) )
            return false;

        // check end
        if( !VerifyUtilities.verifyTime( args[index+2] ) )
            return false;

        // check remaining args
        if( args.length - 1 > index + 2 )
        {
            String[] argsRemaining = Arrays.copyOfRange(args, index+3, args.length);

            index = 0;
            boolean commentFlag = false;
            int comments = 0;
            boolean repeatFlag = false;
            boolean dateFlag = false;

            for (String arg : argsRemaining)
            {
                if(commentFlag&&arg.endsWith("\""))
                {
                    comments++;
                    if (!VerifyUtilities.verifyString(Arrays.copyOfRange(argsRemaining, index - comments, index+1)))
                        return false;
                    commentFlag = false;
                    comments = 0;
                }
                else if (dateFlag)
                {
                    if (!VerifyUtilities.verifyDate(arg))
                        return false;
                    dateFlag = false;
                }
                else if (repeatFlag)
                {
                    if (!VerifyUtilities.verifyRepeat(arg))
                        return false;
                    repeatFlag = false;
                }
                else if (commentFlag)
                    comments++;
                else
                {
                    if (arg.startsWith("\""))
                        commentFlag = true;
                    else if (arg.equals("repeat"))
                        repeatFlag = true;
                    else if (arg.equals("date"))
                        dateFlag = true;
                }
                index++;
            }
        }

        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        ArrayList<ScheduleEntry> entries = Main.scheduleManager.getEntriesByGuild( event.getGuild().getId() );

        if( entries != null && entries.size() >= maxEntries && maxEntries > 0)
        {
            String msg = "Your guild already has the maximum allowed amount of event entries."
                    +" No more entries may be added until old entries are destroyed.";
            MessageUtilities.sendMsg( msg, event.getChannel(), null );
            return;
        }

        String eTitle = "";
        LocalTime eStart = LocalTime.now().plusMinutes(1);    // initialized just in case verify failed it's duty
        LocalTime eEnd = LocalTime.MIDNIGHT;                  //
        ArrayList<String> eComments = new ArrayList<>();      //
        int eRepeat = 0;                                      // default is 0 (no repeat)
        LocalDate eDate = LocalDate.now();

        String buffComment = "";    // String to generate comments strings to place in eComments

        boolean titleFlag = false;    // true if 'eTitle' has been grabbed from args
        boolean startFlag = false;    // true if 'eStart' has been grabbed from args
        boolean endFlag = false;      // true if 'eEnd' has been grabbed from args
        boolean commentFlag = false;  // true if a comment argument was found and is being processed,
                                      // false when the last arg forming the comment is found
        boolean repeatFlag = false;   // true if an arg=='repeat' when flag4 is not flagged
                                      // when true, reads the next arg
        boolean dateFlag = false;

        for( String arg : args )
        {
            if(!titleFlag)
            {
                if( arg.endsWith("\"") )
                {
                    titleFlag = true;
                    eTitle += arg.replace("\"", "");
                }
                else
                    eTitle += arg.replace("\"", "") + " ";
            }
            else if(!startFlag)
            {
                startFlag = true;
                eStart = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else if(!endFlag)
            {
                endFlag = true;
                eEnd = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else
            {
                if( repeatFlag )
                {
                    if( arg.equals("daily") )
                        eRepeat = 1;
                    else if( arg.equals("weekly") )
                        eRepeat = 2;
                    else if( Character.isDigit(arg.charAt(0)) && Integer.parseInt(arg)==1 )
                        eRepeat = 1;
                    else if ( Character.isDigit(arg.charAt(0)) && Integer.parseInt(arg)==2)
                        eRepeat = 2;
                    repeatFlag = false;
                }
                if( !commentFlag && !dateFlag && arg.equals("repeat") )
                {
                    repeatFlag = true;
                }

                if( dateFlag )
                {
                    if( arg.toLowerCase().equals("today") )
                        eDate = LocalDate.now();
                    else if( arg.toLowerCase().equals("tomorrow") )
                        eDate = LocalDate.now().plusDays( 1 );
                    else if( Character.isDigit(arg.charAt(0)) )
                    {
                        eDate = eDate.withMonth(Integer.parseInt(arg.split("/")[0]));
                        eDate = eDate.withDayOfMonth(Integer.parseInt(arg.split("/")[1]));
                    }
                    dateFlag = false;
                }
                else if( !commentFlag && !repeatFlag && arg.equals("date"))
                {
                    dateFlag = true;
                }

                if( arg.startsWith("\"") )
                    commentFlag = true;
                if( commentFlag )
                    buffComment += arg.replace("\"","");
                if( arg.endsWith("\"") )
                {
                    commentFlag = false;
                    eComments.add(buffComment);
                    buffComment = "";
                }
                else if( commentFlag )
                    buffComment += " ";
            }
        }

        ZonedDateTime s = ZonedDateTime.of( eDate, eStart, ZoneId.systemDefault() );
        ZonedDateTime e = ZonedDateTime.of( eDate, eEnd, ZoneId.systemDefault() );

        // generate the event entry message
        String msg = ScheduleEntryParser.generate( eTitle, s, e, eComments, eRepeat, null, event.getGuild().getId() );

        MessageUtilities.sendMsg( msg, event.getGuild().getTextChannelsByName(scheduleChan, false).get(0),
                Main.scheduleManager::addEntry);
    }
}
