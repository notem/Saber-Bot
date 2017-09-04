package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class DeleteCommand implements Command
{
    @Override
    public String name()
    {
        return "delete";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <ID|channel|'all'>```\n" +
                "The delete command can be used to delete schedules or events.\n" +
                "The command's single argument may be an entry's ID, a schedule's channel, or ``all``." +
                "\nIf ``all`` is used all schedules will be deleted, use with caution.\n\n" +
                "The delete command is not required to remove messages or channels." +
                "\nManually deleting the event's message on the schedule channel" +
                " or deleting the entire schedule channel through discord suffices.";

        String USAGE_BRIEF = "``" + head + "`` - remove schedules or events ";

        String USAGE_EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " 084c``" +
                "\n``" + head + " all``" +
                "\n``" + head + " #events``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + USAGE_EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        if (args.length>1)
        {
            return "Too many arguments! Use ``" + head + " <ID|channel|'all'>``";
        }
        if (args.length==0)
        {
            return "Not enough arguments! Use ``" + head + " <ID|channel|'all'>``";
        }

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
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        try
        {
            if( args[0].equals("all") )
            {
                // delete all schedule
                Main.getScheduleManager().getSchedulesForGuild(event.getGuild().getId())
                        .forEach(cId -> Main.getScheduleManager().deleteSchedule(cId));
                MessageUtilities.sendMsg("All events and schedules for this guild has been cleared.", event.getChannel(), null);
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
                MessageUtilities.sendMsg("The event with :id: " + entryId + " removed.", event.getChannel(), null);
            }
            else
            {
                // delete schedule
                Main.getScheduleManager().deleteSchedule(args[0].replace("<#","").replace(">",""));
                MessageUtilities.sendMsg("That schedule has been removed", event.getChannel(), null);
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
