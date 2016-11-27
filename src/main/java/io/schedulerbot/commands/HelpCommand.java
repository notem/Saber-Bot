package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 * file: HelpCommand.java
 *
 * the command which causes the bot to message the event's parent user with
 * the bot operation command list/guide.
 */
public class HelpCommand implements Command
{

    private final String INTRO = "I am **" + Main.jda.getSelfUser().getName() + "**, the task scheduling discord bot." +
            " I can provide your discord with basic event schedule management.  Invite me to your discord and set up " +
            "my appropriate channels to get started.\n\n";

    private static final String USAGE_EXTENDED = "\nTo get detailed information concerning the usage of any of these" +
            " commands use the command **!help <command>** where the prefix for <command> is stripped off. " +
            "Ex. **!help create**";

    private static final String USAGE_BRIEF = "**" + BotConfig.PREFIX + "help** - Messages the user help messages.";

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
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // send the bot intro with a brief list of commands to the user
        if(args.length > 1)
        {
            String commandsBrief = "";
            for( Command cmd : Main.commands.values() )
                commandsBrief += cmd.help( true ) + "\n";

            Main.sendPrivateMsg( INTRO + "__**Available commands**__\n" +
                    commandsBrief + USAGE_EXTENDED, event.getAuthor() );
        }
        // otherwise read search the commands for the first arg
        else
        {
            String helpMsg = Main.commands.get( args[0] ).help( false );
            Main.sendPrivateMsg( helpMsg, event.getAuthor() );
        }

        Main.deleteMsg( event.getMessage() );
    }
}
