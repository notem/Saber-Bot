package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 * clears the schedule locks
 */
public class ClearLocksCommand implements Command
{
    @Override
    public String name()
    {
        return "clear";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        return null;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        Main.getScheduleManager().clearLocks();
        MessageUtilities.sendPrivateMsg("Cleared locks!", event.getAuthor(), null);
    }
}
