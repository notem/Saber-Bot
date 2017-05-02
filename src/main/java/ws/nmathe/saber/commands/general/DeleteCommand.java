package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class DeleteCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "delete";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <ID|channel|'all'>```\n" +
                "The delete command can be used to delete schedules or events.\n" +
                "``<argument>`` may be an entry's ID, a schedule's channel, or ``all``." +
                "\nIf ``all`` is used all schedules will be deleted, use with caution.\n\n" +
                "The delete command is not required." +
                "\nManually deleting the event's message on the schedule channel" +
                " or deleting the entire schedule channel through discord suffices.";

        String USAGE_BRIEF = "``" + invoke + "`` - remove schedules or events ";

        String USAGE_EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " 084c``" +
                "\n``" + invoke + " all``" +
                "\n``" + invoke + " #events``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + USAGE_EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if (args.length>1)
            return "Too many arguments! Use ``" + invoke + " <ID|channel|'all'>``";
        if (args.length==0)
            return "Not enough arguments! Use ``" + invoke + " <ID|channel|'all'>``";

        // pass if "all"
        if (args[0].equals("all"))
        {
            return "";
        }

        // checks to verify arg is hex and entry exists
        if (VerifyUtilities.verifyHex(args[0]))
        {
            Integer entryId = Integer.decode("0x" + args[0]);
            ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
            return "";
        }
        else // arg should be a schedule id
        {
            if(!Main.getScheduleManager().isASchedule(args[0].replace("<#","").replace(">","")))
            {
                return "Argument ``" + args[0] + "`` is not a schedule channel or an event id!";
            }
            return "";
        }
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        if( args[0].equals("all") )
        {
            // delete all schedule
            Main.getScheduleManager().getSchedulesForGuild(event.getGuild().getId())
                    .forEach(cId -> Main.getScheduleManager().deleteSchedule(cId));
        }
        else if(VerifyUtilities.verifyHex(args[0]))
        {
            // delete single event
            Integer entryId = Integer.decode("0x" + args[0]);
            ScheduleEntry entry = Main.getEntryManager().getEntry(entryId);
            Message msg = entry.getMessageObject();
            if( msg==null )
                return;

            Main.getEntryManager().removeEntry(entryId);
            MessageUtilities.deleteMsg(msg, null);
        }
        else
        {
            // delete schedule
            Main.getScheduleManager().deleteSchedule(args[0].replace("<#","").replace(">",""));
        }
    }
}
