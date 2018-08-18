package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 * used to remove events and schedules
 */
public class DeleteCommand implements Command
{
    @Override
    public String name()
    {
        return "delete";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String cmd = prefix + this.name();
        String usage = "``" +cmd+ "`` - remove schedules or events ";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.CORE);

        String cat1 = "- Usage\n"+cmd+" <ID|channel|'all'>";
        String cont1 = "The delete command can be used to delete schedules or events.\n" +
                        "The command's single argument may be an entry's ID, a schedule's channel, or ``all``." +
                        "\nIf ``all`` is used all schedules will be deleted, use with caution.\n\n" +
                        "The delete command is not required to remove messages or channels." +
                        "\nManually deleting the event's message on the schedule channel" +
                        " or deleting the entire schedule channel through discord suffices.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(cmd+" AOja9B");
        info.addUsageExample(cmd+" all");
        info.addUsageExample(cmd+" #events");

        return info;
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
        if (args[0].toLowerCase().equals("all"))
        {
            return "";
        }

        // checks to verify arg is hex and entry exists
        if (VerifyUtilities.verifyEntryID(args[0]))
        {
            Integer entryId = ParsingUtilities.encodeIDToInt(args[0]);
            ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
            return "";
        }
        else // arg should be a schedule id
        {
            if(!Main.getScheduleManager().isSchedule(args[0].replaceAll("[^\\d]","")))
            {
                return "Argument ``" + args[0] + "`` is not a schedule channel or an event id!";
            }
            return "";
        }
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        if(args[0].toLowerCase().equals("all"))
        {
            // delete all schedule
            Main.getScheduleManager().getSchedulesForGuild(event.getGuild().getId())
                    .forEach(cId -> Main.getScheduleManager().deleteSchedule(cId));
            MessageUtilities.sendMsg("All events and schedules for this guild has been cleared.",
                    event.getChannel(), null);
        }
        else if(VerifyUtilities.verifyEntryID(args[0]))
        {
            // delete single event
            Integer entryId = ParsingUtilities.encodeIDToInt(args[0]);
            ScheduleEntry entry = Main.getEntryManager().getEntry(entryId);
            Message msg = entry.getMessageObject();
            if( msg==null )
                return;

            Main.getEntryManager().removeEntry(entryId);
            MessageUtilities.deleteMsg(msg, null);
            MessageUtilities.sendMsg("The event with :id: " +
                    ParsingUtilities.intToEncodedID(entryId) + " removed.", event.getChannel(), null);
        }
        else
        {
            // delete schedule
            Main.getScheduleManager().deleteSchedule(args[0].replaceAll("[^\\d]",""));
            MessageUtilities.sendMsg("That schedule has been removed", event.getChannel(), null);
        }
    }
}
