package ws.nmathe.saber.core.command;

import ws.nmathe.saber.Main;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * used to create a CommandContainer object which contains the parsed tokens of the user input
 * along with the originating event object
 */
class CommandParser
{
    /**
     * parses a MessageEvent containing a command into it's parts
     * @param e event
     * @return container holding command parts
     */
    CommandContainer parse(MessageReceivedEvent e)
    {
        String raw = e.getMessage().getRawContent();

        /// trim off the prefix
        String trimmed;
        if( raw.startsWith( Main.getBotSettings().getAdminPrefix() ))
            trimmed = raw.replaceFirst(Main.getBotSettings().getAdminPrefix(), "");
        else
            trimmed = raw.replaceFirst( Main.getBotSettings().getCommandPrefix(), "");

        // split at spaces
        String[] split = trimmed.split(" ");

        // separate out first arg
        String invoke = split[0];

        // divide out the remaining args from the first arg
        split = Arrays.copyOfRange(split, 1, split.length);

        // process the remaining elements into arguments in a temporary ArrayList
        ArrayList<String> list = new ArrayList<>();
        String tmp = "";            // temporary buffer used when encountering a "txt txt" arg
        boolean quotesFlag = true;  //
        for( String str : split )
        {
            if( quotesFlag )
            {
                if (str.startsWith("\""))
                {
                    tmp += str;
                    if(!str.endsWith("\""))
                        quotesFlag = false;
                }
                else
                {
                    list.add( str.replace("\"","") );
                }
            }
            else
            {
                if( str.endsWith("\"") )
                {
                    tmp += " " + str;
                    list.add(tmp.replace("\"", ""));

                    tmp = "";
                    quotesFlag = true;
                }
                else
                {
                    tmp += " " + str;
                }
            }
        }

        // ArrayList to primitive array
        String[] args = list.toArray(new String[list.size()]);

        return new CommandContainer(invoke, args, e);
    }

    /**
     * an object that holds the parsed user input in the MessageReceivedEvent e.
     **/
    class CommandContainer
    {
        final String invoke;         // the first argument in the user's input
        final String[] args;         // all arguments after the initial argument
        final MessageReceivedEvent event;    // the originating event

        // constructor for CommandContainer
        CommandContainer(String invoke, String[] args, MessageReceivedEvent e)
        {
            this.invoke = invoke;
            this.args = args;
            this.event = e;
        }
    }
}
