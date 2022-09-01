package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collection;

/**
 * messages all connected guilds
 */
public class GlobalMsgCommand implements Command
{

    @Override
    public String name()
    {
        return "announcement";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        return null;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        String msg = "";
        for(String arg : args)
        {
            msg += arg + " ";
        }

        for(Guild guild : Main.getShardManager().getGuilds())
        {
            String channelId = Main.getGuildSettingsManager().getGuildSettings(guild.getId()).getCommandChannelId();
            if(channelId == null) // look for default control channel name
            {
                Collection<TextChannel> chans = guild.getTextChannelsByName( Main.getBotSettingsManager().getControlChan(), true );
                for( TextChannel chan : chans )
                {
                    MessageUtilities.sendMsg(msg, chan, null);
                }
            }
            else // send to configured control channel
            {
                MessageChannel chan = guild.getTextChannelById(channelId);
                if(chan != null)
                {
                    MessageUtilities.sendMsg(msg, chan, null);
                }
            }
        }
        MessageUtilities.sendPrivateMsg("Finished sending announcements to guilds!", event.getAuthor(), null);
    }
}
