package io.schedulerbot.commands.general;

import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 * file: AnnounceCommand.java
 *
 * command which causes the bot to send a message to the ANNOUNCE_CHAN channel
 */
public class AnnounceCommand implements Command
{
    private static final String USAGE_EXTENDED = "\nThe command with form **!announce YOUR MESSAGE HERE** " +
            "will echo YOUR MESSAGE HERE verbatim to the announce channel. Functionality is will likely be " +
            "extended in the future.";

    private static final String USAGE_BRIEF = "**" + BotConfig.PREFIX + "announce** - Send out an announcement" +
            " message to #" + BotConfig.ANNOUNCE_CHAN + ", or to your guild's default public channel.";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }

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

        MessageUtilities.sendAnnounce( msg, event.getGuild(), null );
    }
}
