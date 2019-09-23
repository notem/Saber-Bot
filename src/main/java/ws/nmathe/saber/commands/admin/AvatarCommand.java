package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AccountManager;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.io.File;
import java.io.IOException;

/**
 * updates the bot's discord avatar using an image uploaded in the command message
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
        AccountManager manager = Main.getShardManager().getJDA().getSelfUser().getManager();
        if(event.getMessage().getAttachments().isEmpty()) return;

        Message.Attachment attachment = event.getMessage().getAttachments().get(0);
        try
        {
            File file = new File(attachment.getFileName());
            attachment.downloadToFile(file);

            Icon icon = Icon.from(file);
            manager.setAvatar(icon).complete();

            MessageUtilities.sendPrivateMsg("Updated bot avatar!", event.getAuthor(), null);
            if (!file.delete())
            {
                file.deleteOnExit();
            }
        }
        catch (IOException e)
        {
            Logging.exception(this.getClass(), e);
            MessageUtilities.sendPrivateMsg("Failed to update bot avatar!", event.getAuthor(), null);
        }

    }
}
