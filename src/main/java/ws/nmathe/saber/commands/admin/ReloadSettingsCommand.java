package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

/**
 * reloads the saber.toml settings
 */
public class ReloadSettingsCommand implements Command
{
    @Override
    public String name()
    {
        return "reload";
    }

    @Override
    public CommandInfo info(String head)
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
        MessageUtilities.sendPrivateMsg("Reloaded bot settings!", event.getAuthor(), null);
    }
}
