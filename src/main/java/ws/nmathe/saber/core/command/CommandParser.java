package ws.nmathe.saber.core.command;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.utils.Logging;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * used to create a CommandContainer object which contains the parsed tokens of the user input
 * along with the originating event object
 *
 * strings are parsed into tokens which are space separated. Tokens may include
 * a space if the token is enclosed in quotations ("a token")
 */
class CommandParser
{
    /**
     * parses a MessageEvent containing a command into it's parts
     * @param e event
     * @return container holding command parts
     */
    CommandContainer parse(MessageReceivedEvent e, String prefix)
    {
        String raw = e.getMessage().getRawContent();

        /// trim off the prefix
        String trimmed = StringUtils.replaceOnce(raw,prefix, "").trim();

        // split at white spaces (non newlines)
        String[] split = trimmed.split("[^\\S\n\r]+");

        // separate out first arg
        String invoke = split[0];

        // divide out the remaining args from the first arg
        split = Arrays.copyOfRange(split, 1, split.length);

        // process the remaining elements into arguments in a temporary ArrayList
        ArrayList<String> list = new ArrayList<>();
        String tmp = "";            // temporary buffer used when encountering a "txt ... txt" arg
        boolean quotesFlag = true;  //
        for( String str : split )
        {
            if( quotesFlag )
            {
                if (str.startsWith("\""))
                {
                    tmp += str;
                    quotesFlag = false;

                    if(str.endsWith("\""))
                    {
                        list.add( tmp.replace("\"","") );
                        tmp = "";
                        quotesFlag = true;
                    }
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

        return new CommandContainer(prefix, invoke, args, e);
    }

    /**
     * an object that holds the parsed user input in the MessageReceivedEvent e.
     **/
    class CommandContainer
    {
        final String prefix;            // command prefix
        final String invoke;            // the first argument in the user's input
        final String[] args;            // all arguments after the initial argument
        final MessageReceivedEvent event;    // the originating event

        // constructor for CommandContainer
        CommandContainer(String prefix, String invoke, String[] args, MessageReceivedEvent e)
        {
            this.prefix = prefix;
            this.invoke = invoke.toLowerCase();
            this.args = args;
            this.event = e;
        }
    }
}
