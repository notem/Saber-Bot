package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

/**
 * used to test an event's announcement format
 */
public class TestCommand implements Command
{
    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - test an event's announcement message";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <ID> [<type>]";
        String cont1 = "The test command will send an test announcement for the event to the channel in which the command was used.\n" +
                        "The announcement message for an event is controlled by the schedule to which the event belongs to, and " +
                        "can be changed using the ``config`` command." +
                        "\n\n" +
                        "An optional ``<type>`` argument can be supplied.\n" +
                        "The ``<type>`` argument is used to determine which announcement type to test.\n" +
                        "Valid options are: **start**, **end**, and **remind**.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head + " J09DlA");
        info.addUsageExample(head + " J09DlA end");
        info.addUsageExample(head + " J09DlA remind");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
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
        if(!VerifyUtilities.verifyEntryID(args[index]))
        {
            return "``" + args[index] + "`` is not a valid entry ID!";
        }

        // check to see if event with the provided ID exists for the guild
        Integer Id = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );
        if(entry == null)
        {
            return "I could not find an entry with that ID!";
        }

        index++; // next argument is optional

        if(args.length == 2)
        {
            switch(args[index].toLowerCase())
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
    public void action(String prefix, String[] args, EventCompat event)
    {
        int index = 0;

        // get entry object
        Integer entryId = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

        // verify the entry's message exists
        Message msg = entry.getMessageObject();
        if(msg == null) return;

        index++;

        String format = Main.getScheduleManager().getStartAnnounceFormat(entry.getChannelId());
        if(args.length == 2)
        {
            switch(args[index].toLowerCase())
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

        String remindMsg = ParsingUtilities.processText(format, entry, true);
        MessageUtilities.sendMsg(remindMsg, event.getChannel(), null);
    }
}
