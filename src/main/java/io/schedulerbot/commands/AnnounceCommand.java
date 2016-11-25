package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

/**
 * file: AnnounceCommand.java
 *
 * command which causes the bot to send a message to the ANNOUNCE_CHAN channel
 */
public class AnnounceCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        // can't announce a message that doesn't exist!
        return args.length > 0;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // reform the args into one string
        String msg = "";
        for( String str : args )
           msg += " " + str;

        try
        {
            // message out
            if(BotConfig.ANNOUNCE_CHAN.isEmpty())
                event.getGuild().getPublicChannel()
                        .sendMessage( msg ).queue();
            else
                event.getGuild().getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                        .sendMessage( msg ).queue();
        }
        catch( PermissionException e )
        {
            Main.handleException( e, event );
        }

    }
}
