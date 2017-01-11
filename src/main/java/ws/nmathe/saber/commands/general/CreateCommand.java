package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntryParser;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * CreateCommand places a new entry message on the discord schedule channel
 * a ScheduleEntry is not created until the message sent by this command is parsed by
 * the listener
 */
public class CreateCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private int maxEntries = Main.getBotSettings().getMaxEntries();
    private ScheduleManager schedManager = Main.getScheduleManager();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "Event entries can be initialized using the form **" + prefix +
                "create \"TITLE\" <Start> <End> <Optional>**. Entries MUST be initialized with a title, a start " +
                "time, and an end time. If your guild has multiple scheduling channels, an additional argument " +
                "indicating the channel must go before the title. Start and end times should be of form h:mm with " +
                "optional am/pm appended on the end. " +
                "\n\nEntries can optionally be configured with comments, repeat, and a start date. Adding **repeat no**/**daily**/**weekly** to " +
                "**<Optional>** will configure repeat; default behavior is no repeat. Adding **date MM/dd** to " +
                "**<Optional>** will configure the start date; default behavior is to use the current date or the " +
                "next day depending on if the current time is greater than the start time. Comments may be added by" +
                " adding **\"YOUR COMMENT\"** in **<Optional>**; any number of comments may be added in **<Optional>" +
                "**.";

        String EXAMPLES = "Ex1. **!create \"Party in the Guild Hall\" 19:00 02:00**" +
                "\nEx2. **!create \"event_channel Reminders\" \"Sign up for Raids\" 4:00pm 4:00pm**" +
                "\nEx3. **!create \"Weekly Raid Event\" 7:00pm 12:00pm repeat weekly \"Healers and tanks always in " +
                "demand.\" \"PM our raid captain with your role and level if attending.\"**";

        String USAGE_BRIEF = "**" + prefix + "create** - Generates a new event entry" +
                " and sends it to the specified schedule channel.";

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
            return "Not enough arguments";

        // check channel
        String tmp = "";
        if (args[index].startsWith("\"") && args[index].endsWith("\""))
        {
            tmp = args[index].replace("\"", "");
            index++;
        }
        else if (args[index].startsWith("\""))
        {
            for (; index < args.length - 1; index++)
            {
                tmp += args[index];
                if (args[index].endsWith("\""))
                    break;
            }

            if( !tmp.endsWith("\"") )
                return "Invalid argument '" + tmp + "', missing ending \"";
        }
        else
        {
            return "Invalid argument '" + args[index] + "'";
        }

        // check title
        if (args[index].startsWith("\"") && args[index].endsWith("\""))
        {
            Collection<TextChannel> chans = event.getGuild().getTextChannelsByName(tmp, true);
            if (chans.isEmpty())
                return "Schedule channel '" + tmp + "' does not exist";
        }
        else if (args[index].startsWith("\""))
        {
            Collection<TextChannel> chans = event.getGuild().getTextChannelsByName(tmp, true);
            if (chans.isEmpty())
                return "Schedule channel '" + tmp + "' does not exist";

            tmp = "";
            for (; index < args.length - 1; index++)
            {
                tmp += args[index];
                if (args[index].endsWith("\""))
                    break;
            }

            if( !tmp.endsWith("\"") )
                return "Invalid argument '" + tmp + "', missing ending \"";
        }
        else
        {
            Collection<TextChannel> schedChans = GuildUtilities.getValidScheduleChannels(event.getGuild());
            if( schedChans.size() != 1 )
                return "Not enough arguments";
        }

        // check that there are enough args remaining for start and end
        if( args.length - 1  < index + 2 )
            return "Not enough arguments";

        // check start
        if( !VerifyUtilities.verifyTime( args[index+1] ) )
            return "Invalid argument '" + args[index+1] + "', expected a start time";

        // check end
        if( !VerifyUtilities.verifyTime( args[index+2] ) )
            return "Invalid argument '" + args[index+2] + "', expected an end time";

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
                        return "Invalid argument '" + arg + "', expected the end of a comment";
                    commentFlag = false;
                    comments = 0;
                }
                else if (dateFlag)
                {
                    if (!VerifyUtilities.verifyDate(arg))
                        return "Invalid argument '" + arg + "', expected a date";
                    dateFlag = false;
                }
                else if (repeatFlag)
                {
                    if (!VerifyUtilities.verifyRepeat(arg))
                        return "Invalid argument '" + arg + "', expected a repeat option";
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
            }
        }

        ArrayList<Integer> entries = schedManager.getEntriesByGuild( event.getGuild().getId() );
        if( entries != null && entries.size() >= maxEntries && maxEntries > 0)
        {
            return "Your guild has the maximum allowed amount of schedule entries."
                    +" No more entries may be added until old entries are destroyed.";
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String eTitle = "";
        LocalTime eStart = LocalTime.now().plusMinutes(1);    // initialized just in case verify failed it's duty
        LocalTime eEnd = LocalTime.MIDNIGHT;                  //
        ArrayList<String> eComments = new ArrayList<>();      //
        int eRepeat = 0;                                      // default is 0 (no repeat)
        LocalDate eDate = LocalDate.now();                    // initialize date using the current date
        TextChannel scheduleChan = GuildUtilities.getValidScheduleChannels(event.getGuild()).get(0);
        String channelName = "";

        String buffComment = "";    // String to generate comments strings to place in eComments

        boolean channelFlag = false;
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
            if(!channelFlag)
            {
                if( arg.endsWith("\"") )
                {
                    channelFlag = true;
                    channelName += arg.replace("\"", "");
                    List<TextChannel> chans = event.getGuild().getTextChannelsByName(channelName,true);
                    if( chans.isEmpty() )
                    {
                        titleFlag = true;
                        eTitle = channelName;
                    }
                }
                else
                {
                    channelName += arg.replace("\"", "") + " ";
                }
            }
            else if(!titleFlag)
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
        String msg = ScheduleEntryParser.generate( eTitle, s, e, eComments, eRepeat, null, scheduleChan.getId() );
        __out.printOut(this.getClass(), scheduleChan.getName());

        MessageUtilities.sendMsg( msg,
                scheduleChan,
                schedManager::addEntry );
    }
}
