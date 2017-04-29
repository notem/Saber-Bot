package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

/**
 */
public class SortCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "sort";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <channel>```\n" +
                "The sort command will re-sort the entries in a schedule.  Entries are reordered so that " +
                "the top event entry is the next event to begin. The schedule cannot be modified while" +
                " in the process of sorting.  Schedules with more than 10 entries will not be sorted.";

        String USAGE_BRIEF = "``" + invoke + "`` - reorder the schedule by start time";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " #schedule``\n";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 1)
            return "That's not enough arguments! Use ``" + invoke + " <channel>``";

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + invoke + "`` command to create a new schedule!";

        if(Main.getScheduleManager().isLocked(cId))
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes. " +
                    "(If this does not go away ping @notem in the support server)";

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        String cId = args[index].replace("<#","").replace(">","");

        Main.getScheduleManager().sortSchedule(cId);
    }
}
