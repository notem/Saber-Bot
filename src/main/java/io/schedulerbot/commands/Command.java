package io.schedulerbot.commands;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 * file: Command.java
 *
 * Interface to be implemented by all bot commands
 */
public interface Command
{

    /**
     * the function which retrieves the help text for the command
     * @param brief true - return the brief help text, false - return the full help text
     * @return String, the description and operation of the command
     */
    String help( boolean brief );


    /**
     * used to verify that the argument string for the invoking argument
     * is properly formed
     * @param args array of argument strings
     * @param event the originating event
     * @return true if arguments are properly formed, false otherwise
     */
    String verify(String[] args, MessageReceivedEvent event);


    /**
     * what the bot does when the command is called by the user
     * @param args an array of arguments provided with the commands (excludes the invoking argument)
     * @param event the originating event object
     */
    void action(String[] args, MessageReceivedEvent event);
}
