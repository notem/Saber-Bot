package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 * rearranges the events on a schedule
 */
public class SortCommand implements Command
{
    @Override
    public String name()
    {
        return "sort";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - reorder the schedule by start time";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <channel> [<order>]";
        String cont1 = "The sort command will re-sort the entries in a schedule." +
                "\nEntries can be reordered in either ascending or descending order by adding 'asc' or 'desc' to the command.\n" +
                "If the order is omitted from the command, the schedule will be sorted in ascending order." +
                "\n\n" +
                "The schedule cannot be modified while in the process of sorting.\n" +
                "If for some reason your schedule is not unlocked after 10 minutes, visit the support server!\n" +
                "For performance reasons, schedules with more than 15 entries will not be sorted.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head + " #schedule");
        info.addUsageExample(head + " #schedule desc");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        // check arg lengths
        if(args.length > 2)
        {
            return "That's too many arguments!\n" +
                    "Use ``" + head + " <channel> [<order>]``";
        }
        if(args.length < 1)
        {
            return "That's not enough arguments!\n" +
                    "Use ``" + head + " <channel> [<order>]``";
        }

        // check channel
        String cId = args[index].replaceAll("[^\\d]","");
        if( !Main.getScheduleManager().isSchedule(cId) )
        {
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + prefix + "init`` command to create a new schedule!";
        }
        if(Main.getScheduleManager().isLocked(cId))
        {
            return "Schedule is locked while sorting or syncing. Please try again after I finish.";
        }

        // check for optional arguments
        if(args.length == 2)
        {
            switch(args[1].toLowerCase())
            {
                case "asc":
                case "ascend":
                case "ascending":
                case "desc":
                case "descend":
                case "descending":
                    break;

                default:
                    return "*" + args[1] + "* is not a valid sorting order! Use either *asc* or *desc*.";
            }
        }

        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        String cId = args[index].replaceAll("[^\\d]","");

        if(args.length <= 1 || args[1].toLowerCase().startsWith("asc"))
        {
            Main.getScheduleManager().sortSchedule(cId, false);
        }
        else if(args.length > 1 && args[1].toLowerCase().startsWith("desc"))
        {
            Main.getScheduleManager().sortSchedule(cId, true);
        }

        String content = "I have finished sorting <#" + cId + ">!";
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
