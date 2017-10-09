package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.entities.Icon;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.managers.AccountManagerUpdatable;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.io.File;
import java.io.IOException;

/**
 * reloads the saber.toml settings
 */
public class AvatarCommand implements Command
{
    @Override
    public String name()
    {
        return "avatar";
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
        AccountManagerUpdatable manager = Main.getShardManager().getJDA().getSelfUser().getManagerUpdatable();
        if(event.getMessage().getAttachments().isEmpty()) return;

        Message.Attachment attachment = event.getMessage().getAttachments().get(0);
        try
        {
            File file = new File(attachment.getFileName());
            attachment.download(file);

            Icon icon = Icon.from(file);
            manager.getAvatarField().setValue(icon).update().complete();

            MessageUtilities.sendPrivateMsg("Updated bot avatar!", event.getAuthor(), null);
            file.delete();
        }
        catch (IOException e)
        {
            Logging.exception(this.getClass(), e);
            MessageUtilities.sendPrivateMsg("Failed to update bot avatar!", event.getAuthor(), null);
        }

    }
}
