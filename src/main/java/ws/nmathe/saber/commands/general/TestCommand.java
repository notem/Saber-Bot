package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

/**
 */
public class TestCommand implements Command
{
    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <ID> [<type>]```\n" +
                "The test command will send an test announcement for the event to the channel in which the command was used.\n" +
                "The announcement message for an event is controlled by the schedule to which the event belongs to, and " +
                "can be changed using the ``config`` command.\n\n" +
                "An optional ``<type>`` argument can be supplied.\n" +
                "The ``<type>`` argument is used to determine which announcement type to test.\n" +
                "Valid options are: **start**, **end**, and **remind**.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " 7ffff``\n" +
                "``" + head + " 09aa8 end``";

        String USAGE_BRIEF = "``" + head + "`` - test an event's announcement message";

        if( brief ) return USAGE_BRIEF;
        else return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        int index = 0;

        // length checks
        if(args.length < 1)
        {
            return "That's not enough arguments! Use ``" + head + " <ID>``";
        }
        if(args.length > 2)
        {
            return "That's too many arguments! Use ``" + head + " <ID> [<type>]``";
        }

        // check for a valid entry ID
        if(!VerifyUtilities.verifyBase64(args[index]))
        {
            return "``" + args[index] + "`` is not a valid entry ID!";
        }

        // check to see if event with the provided ID exists for the guild
        Integer Id = ParsingUtilities.base64ToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );
        if(entry == null)
        {
            return "I could not find an entry with that ID!";
        }

        index++; // next argument is optional

        if(args.length == 2)
        {
            switch(args[index])
            {
                case "start":
                case "end":
                case "remind":
                case "reminder":
                case "e":
                case "r":
                case "s":
                    break;

                default:
                    return "*"+args[index]+"* is not an announcement type!\n" +
                            "Please use either ``start``, ``end``, or ``remind`` for this argument!";
            }
        }

        return ""; // return valid
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        try
        {
            int index = 0;

            // get entry object
            Integer entryId = ParsingUtilities.base64ToInt(args[index]);
            ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );
            // verify the entry's message exists
            Message msg = entry.getMessageObject();
            if(msg == null) return;

            index++;

            String format = Main.getScheduleManager().getStartAnnounceFormat(entry.getChannelId());
            if(args.length == 2)
            {
                switch(args[index])
                {
                    case "e":
                    case "end":
                        format = Main.getScheduleManager().getEndAnnounceFormat(entry.getChannelId());
                        break;
                    case "r":
                    case "remind":
                        format = Main.getScheduleManager().getReminderFormat(entry.getChannelId());
                        break;
                }
            }

            String remindMsg = ParsingUtilities.parseMsgFormat(format, entry);
            MessageUtilities.sendMsg(remindMsg, event.getChannel(), null);
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
