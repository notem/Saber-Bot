package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

/**
 * file: HelpCommand.java
 *
 * the command which causes the bot to message the event's parent user with
 * the bot operation command list/guide.
 */
public class HelpCommand implements Command
{

    private final String USAGE = ""
            + "List of available commands:\n"
            + "\t" + BotConfig.PREFIX
            + "help\t:\tSends a private help message to the user who invoked the command.\n"
            + "\t" + BotConfig.PREFIX
            + "create <event> [args]\t:\t[NOT IMPLEMENTED]\n"
            + "\t" + BotConfig.PREFIX
            + "edit <event> [args]\t:\t[NOT IMPLEMENTED]\n"
            + "\t" + BotConfig.PREFIX
            + "announce <msg>\t:\tSends a message to " + BotConfig.ANNOUNCE_CHAN + ".\n";

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        // trailing arguments are irrelevant in the instance of this command
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // send the help USAGE message to the user
        try
        {
            event.getAuthor().openPrivateChannel().queue();
            event.getAuthor().getPrivateChannel().sendMessage(USAGE).queue();
        }
        catch( Exception e )
        {
            Main.handleException( e );
        }
        // and attempt to delete the original help command from chat
        try
        {
            event.getMessage().deleteMessage().queue();
        }
        // catch Permission exceptions (private channel or not assigned permissions in public channel)
        catch( PermissionException e )
        {
            Main.handleException( e );
        }
    }
}
