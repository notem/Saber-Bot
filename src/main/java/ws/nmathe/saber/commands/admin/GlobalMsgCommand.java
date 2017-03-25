package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.__out;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Collection;

/**
 */
public class GlobalMsgCommand implements Command
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
        String msg = "";
        for( String arg : args )
        {
            msg += arg + " ";
        }

        for( Guild guild : Main.getBotJda().getGuilds() )
        {
            Collection<TextChannel> chans = guild.getTextChannelsByName( Main.getBotSettings().getControlChan(), true );
            for( TextChannel chan : chans )
            {
                MessageUtilities.sendMsg(msg, chan, null);
            }
        }
    }
}
