package ws.nmathe.saber.core.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ws.nmathe.saber.Main;

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
public class CommandParser
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

        EventCompat e_compat = new EventCompat(e);

        return new CommandContainer(raw, prefix, invoke, args, e_compat);
    }
    CommandContainer parse(SlashCommandInteractionEvent e, String prefix)
    {
        /// trim off the prefix
        String invoke = e.getName();

        OptionMapping option = e.getOption(Main.getCommandHandler().argName);
        String content;
        if (option == null) content = "";
        else content = option.getAsString();

        // split at white spaces (non newlines) or quotation captures
        Matcher matcher = Pattern.compile("[\"\\u201C\\u201D][\\S\\s]*?[\\u201C\\u201D\"]|[^ \"\\u201C\\u201D]+").matcher(content);
        List<String> list = new ArrayList<>();
        while (matcher.find())
        {
            String group = matcher.group();
            if(!group.isEmpty()) list.add(group.replaceAll("[\"\\u201C\\u201D]",""));
        }

        String[] args = list.stream().toArray(String[]::new);

        EventCompat e_compat = new EventCompat(e);

        return new CommandContainer(invoke, prefix, invoke, args, e_compat);
    }

    /**
     * an object that holds the parsed user input in the MessageReceivedEvent e.
     **/
    class CommandContainer
    {
        final String raw;
        final String prefix;            // command prefix
        final String invoke;            // the first argument in the user's input
        final String[] args;            // all arguments after the initial argument
        final EventCompat event;    // the originating event

        // constructor for CommandContainer
        CommandContainer(String raw, String prefix, String invoke, String[] args, EventCompat e)
        {
            this.prefix = prefix;
            this.invoke = invoke.toLowerCase();
            this.args = args;
            this.event = e;
            this.raw = raw;
        }
    }

    public class EventCompat
    {
        final MessageChannel channel;
        final JDA jda;
        final Guild guild;
        final User user;

        EventCompat(MessageReceivedEvent e)
        {
            this.channel = e.getChannel();
            this.jda = e.getJDA();
            this.guild = e.getGuild();
            this.user = e.getAuthor();
        }
        EventCompat(SlashCommandInteractionEvent e)
        {
            this.channel = e.getChannel();
            this.jda = e.getJDA();
            this.guild = e.getGuild();
            this.user = e.getUser();
        }

        public MessageChannel getChannel() { return this.channel; }
        public JDA getJDA() { return this.jda; } 
        public Guild getGuild() { return this.guild; }
        public User getAuthor() { return this.user; }
        public boolean isFromType(ChannelType type) { return this.channel.getType() == type; }
    }
}
