package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 */
public class SortCommand implements Command
{
    @Override
    public String name()
    {
        return "sort";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <channel> [<order>]```\n" +
                "The sort command will re-sort the entries in a schedule." +
                "\nEntries can be reordered in either ascending or descending order by adding 'asc' or 'desc' to the command.\n" +
                "If the order is omitted from the command, the schedule will be sorted in ascending order." +
                "\n\n" +
                "The schedule cannot be modified while in the process of sorting.\n" +
                "If for some reason your schedule is not unlocked after 10 minutes, visit the support server!\n" +
                "Fore performance reasons, schedules with more than 15 entries will not be sorted.";

        String USAGE_BRIEF = "``" + head + "`` - reorder the schedule by start time";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " #schedule``\n";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        int index = 0;

        if (args.length != 2 && args.length != 1)
            return "That's not enough arguments! Use ``" + head + " <channel> [<order>]``";

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + head + "`` command to create a new schedule!";

        if(Main.getScheduleManager().isLocked(cId))
            return "Schedule is locked while sorting or syncing. Please try again after I finish.";

        if(args.length == 2)
        {
            switch(args[1])
            {
                case "asc":
                case "desc":
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
        String cId = args[index].replace("<#","").replace(">","");

        if(args.length <= 1 || args[1].equalsIgnoreCase("asc"))
        {
            Main.getScheduleManager().sortSchedule(cId, false);
        }
        else if(args.length > 1 && args[1].equalsIgnoreCase("desc"))
        {
            Main.getScheduleManager().sortSchedule(cId, true);
        }

        String content = "I have finished sorting <#" + cId + ">!";
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
