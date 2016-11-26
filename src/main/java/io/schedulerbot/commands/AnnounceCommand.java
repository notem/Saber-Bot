package io.schedulerbot.commands;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

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

        Main.sendAnnounce( msg, event.getGuild() );
    }
}
