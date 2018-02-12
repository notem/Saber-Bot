package ws.nmathe.saber.core.command;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
        /// trim off the prefix
        String raw = e.getMessage().getContentRaw();
        String trimmed = StringUtils.replaceOnce(raw,prefix, "").trim();

        // split at white spaces (non newlines) or quotation captures
        Matcher matcher = Pattern.compile("[\"\\u201C\\u201D][\\S\\s]*?[\\u201C\\u201D\"]|[^ \"\\u201C\\u201D]+").matcher(trimmed);
        List<String> list = new ArrayList<>();
        while (matcher.find())
        {
            String group = matcher.group();
            if(!group.isEmpty()) list.add(group.replaceAll("[\"\\u201C\\u201D]",""));
        }

        String[] args = list.stream().toArray(String[]::new);

        // separate out first arg
        String invoke = args[0];

        // divide out the remaining args from the first arg
        args = Arrays.copyOfRange(args, 1, args.length);

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
