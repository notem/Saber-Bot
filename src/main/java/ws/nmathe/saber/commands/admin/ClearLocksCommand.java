package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 */
public class ClearLocksCommand implements Command
{
    @Override
    public String name()
    {
        return "clear";
    }

    @Override
    public String help(String prefix, boolean brief)
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
