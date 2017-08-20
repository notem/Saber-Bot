package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 */
public class ReloadSettingsCommand implements Command
{
    @Override
    public String name()
    {
        return "reload";
    }

    @Override
    public String help(String head, boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String head, String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        Main.getBotSettingsManager().reloadSettings();
        Main.getShardManager().loadGamesList();
        MessageUtilities.sendPrivateMsg("Reloaded bot settings!", event.getAuthor(), null);
    }
}
