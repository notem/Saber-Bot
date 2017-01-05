package io.schedulerbot.commands.admin;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.__out;
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
            __out.printOut(this.getClass(),"sending announce to " + guild.getName());

            Collection<TextChannel> chans = guild.getTextChannelsByName( Main.getBotSettings().getControlChan(), false );
            for( TextChannel chan : chans )
            {
                MessageUtilities.sendMsg(msg, chan, null);
            }
        }
    }
}
