package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class StatsCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String msg = "**Current Stats**\n";
        msg += "  Schedule Entries: " + Main.getScheduleManager().getAllEntries().size() + "\n";
        msg += "  Guilds: " + Main.getBotJda().getGuilds().size() + "\n";

        MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
    }
}
