package ws.nmathe.saber.commands;

import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;

/**
 * file: Command.java
 *
 * Interface to be implemented by all bot commands
 */
public interface Command
{
    /**
     * the function which retrieves the invoking name for the command
     * @return String, name of command
     */
    String name();

    /**
     * the function which retrieves the info text for the command
     * @param prefix the initial substring of characters denoting the string is a command
     * @return CommandInfo holding relevant command information
     */
    CommandInfo info(String prefix);

    /**
     * used to verify that the argument string for the invoking argument
     * is properly formed
     * @param prefix the initial substring of characters denoting the string is a command
     * @param args array of argument strings
     * @param event the originating event
     * @return true if arguments are properly formed, false otherwise
     */
    String verify(String prefix, String[] args, EventCompat event);

    /**
     * what the bot does when the command is called by the user
     * @param prefix the initial substring of characters denoting the string is a command
     * @param args an array of arguments provided with the commands (excludes the invoking argument)
     * @param event the originating event object
     */
    void action(String prefix, String[] args, EventCompat event);
}
