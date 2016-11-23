package io.schedulerbot.utils;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;

/**
 * file: CommandParser.java
 * used to create a CommandContainer object which contains the parsed tokens of the user input
 * along with the originating event object
 */
public class CommandParser
{
    /**
     * parse takes a MessageReceivedEvent and processes into string tokens saved in a CommandContainer object
     * @precondition user input begins with whatever is set in BotConfig.PREFIX
     */
    public CommandContainer parse(String raw, MessageReceivedEvent e)
    {
        /// trim off the prefix
        String trimmed = raw.replaceFirst(BotConfig.PREFIX, "");
        // split the trimmed string into arguments
        String[] splitTrimmed = trimmed.split(" ");
        // store splitTrimmed into an ArrayList
        ArrayList<String> split = new ArrayList<String>();
        for(String s : splitTrimmed)
            split.add(s);
        // take the first arg
        String invoke = split.get(0);
        // divide out the remaining args from the first arg
        String[] args = new String[split.size() - 1];
        split.subList(1, split.size()).toArray(args);

        return new CommandContainer(raw, trimmed, splitTrimmed, invoke, args, e);
    }

    /** an object that holds the parsed user input in the MessageReceivedEvent e. **/
    public class CommandContainer
    {
        public final String raw;            // the user's input unedited
        public final String trimmed;        // the user input with the '!' removed
        public final String[] splitTrimmed; // an the user's trimmed input split into arguments
        public final String invoke;         // the first argument in the user's input
        public final String[] args;         // all arguments after the initial argument
        public final MessageReceivedEvent event;    // the originating event

        // constructor for CommandContainer
        public CommandContainer(
                String raw,
                String trimmed,
                String[] splitTrimmed,
                String invoke,
                String[] args,
                MessageReceivedEvent e )
        {
            this.raw = raw;
            this.trimmed = trimmed;
            this.splitTrimmed = splitTrimmed;
            this.invoke = invoke;
            this.args = args;
            this.event = e;
        }
    }
}
