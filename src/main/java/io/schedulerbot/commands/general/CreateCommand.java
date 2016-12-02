package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.Scheduler;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.VerifyUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * file: CreateCommand.java
 *
 * CreateCommand places a new event on the EVENT_CHAN text channel
 * Note that the actual EventEntry thread has not yet been created.
 */
public class CreateCommand implements Command
{
    private static final String USAGE_EXTENDED = "\nEvent entries can be initialized using the form **!create " +
            "\"TITLE\" <Start> <End> <Optional>**. Entries MUST be initialized with a title, a start " +
            "time, and an end time. Start and end times should be of form HH:mm. Entries can optionally be " +
            "configured with comments, repeat, and a start date. Adding **repeat no**/**daily**/**weekly** to " +
            "**<Optional>** will configure repeat; default behavior is no repeat. Adding **date MM/dd** to " +
            "**<Optional>** will configure the start date; default behavior is to use the current date or the " +
            "next day depending on if the current time is greater than the start time. Comments may be added by" +
            " adding **\"YOUR COMMENT\"** in **<Optional>**; any number of comments may be added in **<Optional>" +
            "**.\n\nEx1. **!create \"Party in the Guild Hall\" 19:00 02:00**\nEx2. **!create \"Weekly Raid Event\"" +
            " 19:00 22:00 repeat weekly \"Healers and tanks always in demand.\" \"PM our raid captain with your " +
            "role and level if attending.\"**";

    private static final String USAGE_BRIEF = "**" + BotConfig.PREFIX + "create** - Generates a new event entry" +
            " in #" + BotConfig.EVENT_CHAN + ".";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
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
        ArrayList<Integer> entries = Main.getEntriesByGuild( event.getGuild().getId() );

        if( entries != null && entries.size() >= BotConfig.MAX_ENTRIES && BotConfig.MAX_ENTRIES > 0)
        {
            String msg = "Your guild already has the maximum allowed amount of event entries."
                    +" No more entries may be added until old entries are destroyed.";
            MessageUtilities.sendMsg( msg, event.getChannel() );
            return;
        }

        String eTitle = "";
        LocalTime eStart = LocalTime.now().plusMinutes(1);    // initialized just in case verify failed it's duty
        LocalTime eEnd = LocalTime.MIDNIGHT;      //
        ArrayList<String> eComments = new ArrayList<>();
        int eRepeat = 0;             // default is 0 (no repeat)
        LocalDate eDate = LocalDate.now();

        String buffComment = "";    // String to generate comments strings to place in eComments

        boolean flag1 = false;  // true if 'eTitle' has been grabbed from args
        boolean flag2 = false;  // true if 'eStart' has been grabbed from args
        boolean flag3 = false;  // true if 'eEnd' has been grabbed from args
        boolean flag4 = false;  // true if a comment argument was found and is being processed,
                                // false when the last arg forming the comment is found
        boolean flag5 = false;  // true if an arg=='repeat' when flag4 is not flagged
                                // when true, reads the next arg
        boolean flag6 = false;

        for( String arg : args )
        {
            if(!flag1)
            {
                if( arg.endsWith("\"") )
                {
                    flag1 = true;
                    eTitle += arg.replace("\"", "");
                }
                else
                    eTitle += arg.replace("\"", "") + " ";
            }
            else if(!flag2)
            {
                flag2 = true;
                eStart = LocalTime.parse(arg);
            }
            else if(!flag3)
            {
                flag3 = true;
                eEnd = LocalTime.parse(arg);
            }
            else
            {
                if( flag5 )
                {
                    if( arg.equals("daily") )
                        eRepeat = 1;
                    else if( arg.equals("weekly") )
                        eRepeat = 2;
                    else if( Character.isDigit(arg.charAt(0)) && Integer.parseInt(arg)==1 )
                        eRepeat = 1;
                    else if ( Character.isDigit(arg.charAt(0)) && Integer.parseInt(arg)==2)
                        eRepeat = 2;
                    flag5 = false;
                }
                if( !flag4 && !flag6 && arg.equals("repeat") )
                {
                    flag5 = true;
                }

                if( flag6 )
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
                    flag6 = false;
                }
                else if( !flag4 && !flag5 && arg.equals("date"))
                {
                    flag6 = true;
                }

                if( arg.startsWith("\"") )
                    flag4 = true;
                if( flag4 )
                    buffComment += arg.replace("\"","");
                if( arg.endsWith("\"") )
                {
                    flag4 = false;
                    eComments.add(buffComment);
                    buffComment = "";
                }
                else if( flag4 )
                    buffComment += " ";
            }
        }

        // generate the event entry message
        String msg = Scheduler.generate( eTitle, eStart, eEnd, eComments, eRepeat, eDate, null );

        MessageUtilities.sendMsg( msg, event.getGuild().getTextChannelsByName(BotConfig.EVENT_CHAN, false).get(0) );
    }
}
