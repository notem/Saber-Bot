package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * used for generating the list of valid timezone strings
 */
public class SkipCommand implements Command
{
    @Override
    public String name()
    {
        return "skip";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - skips to the next event occurrence";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <id>";
        String cont1 = "This command will cause an event to cancel the current iteration of an event, " +
                "and reschedule the event for the next time the event is set to repeat.\n" +
                "If an event is not configured to repeat, the event will be removed." +
                "\n\n" +
                "It is not possible to undo an event skip, so use with caution.\n" +
                "If you decide you didn't want to skip an event's occurrence after-the-fact, " +
                "you will need to recreate the event!";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head+" 10agj2");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        String head = prefix + this.name();
        if (args.length != 1)
        {
            return "Incorrect amount of arguments!" +
                    "\nUse ``" + head + " <id>``";
        }
        ScheduleEntry entry;
        if (VerifyUtilities.verifyEntryID(args[0]))
        {
            Integer entryId = ParsingUtilities.encodeIDToInt(args[0]);
            entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
        }
        else
        {
            return "Argument *" + args[0] + "* is not a valid entry ID!";
        }
        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        int index = 0;
        Integer entryId = ParsingUtilities.encodeIDToInt(args[index++]);
        ScheduleEntry se = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());

        // send a confirmation to the channel
        String content;
        if(se.getRecurrence().shouldRepeat(se.getStart()))
        {
            se.repeat();
            content = "The event has been cancelled.\n" +
                    "The event is next scheduled for " +
                    se.getStart().format(DateTimeFormatter.ofPattern("MMM d, hh:mm a"));
        }
        else
        {
            se.repeat();
            content = "The event has been cancelled.";
        }
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
