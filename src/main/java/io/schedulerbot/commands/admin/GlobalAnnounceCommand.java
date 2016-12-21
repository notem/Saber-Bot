package io.schedulerbot.commands.admin;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.__out;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class GlobalAnnounceCommand implements Command
{

    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return true;
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
            MessageUtilities.sendAnnounce( msg, guild, null );
        }
    }
}
