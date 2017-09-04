package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.settings.GuildSettingsManager.GuildSettings;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import java.util.ArrayList;

/**
 */
public class GuildCommand implements Command
{
    @Override
    public String name()
    {
        return "guild";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <option> <new config>```\n" +
                "The guild command may be used to modify guild-wide settings such as your guild's command prefix and control channel.\n" +
                "Issuing the guild command without arguments will output the current settings for your discord server.\n\n" +
                "Options for <option> are: ``restrict``, ``unrestrict``, ``prefix``, and ``control``.\n" +
                "\n```diff\n+ (Un)Restricted Commands```\n" +
                "Commands may be configured as either 'restricted' or 'unrestricted' using the ``restrict`` and ``unrestrict`` command options.\n\n" +
                "A restricted command can only be used in the designated control channel.\n" +
                "Unrestricted commands may be used in any channel the bot is allowed to view, and by anyone who can post in those channels.";

        String USAGE_BRIEF = "``" + head + "`` - adjust guild-wide settings";

        String EXAMPLES = "```diff\n- Examples```" +
                "\n``" + head + " control #bot_management``" +
                "\n``" + head + " unrestrict help``" +
                "\n``" + head + " restrict create``" +
                "\n``" + head + " prefix $``";

        if( brief ) return USAGE_BRIEF;
        else return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        if(args.length > 0)
        {
            GuildSettings guildSettings = Main.getGuildSettingsManager().getGuildSettings(event.getGuild().getId());

            switch(args[0])
            {
                case "r":
                case "restrict":
                    if(args.length < 2)
                    {
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " restrict [cmd]`` where ``[cmd]`` " +
                                "is the name of the command you wish to restrict.\n" +
                                "Restricted commands may only be used inside the bot control channel.";
                    }
                    if(!Main.getCommandHandler().getCommandNames().contains(args[1].toLowerCase()))
                    {
                        return "**" + args[1] + "** is not a valid command name! Make sure to not include the prefix!";
                    }
                    if(guildSettings.getRestrictedCommands().contains(args[1]))
                    {
                        return "**" + args[1] + "** is already restricted!";
                    }
                    break;

                case "u":
                case "unrestrict":
                    if(args.length < 2)
                    {
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " restrict [cmd]`` where ``[cmd]`` " +
                                "is the name of the command you wish to unrestrict.\n" +
                                "Unrestricted commands may be used by anyone outside the bot control channel.";
                    }
                    if(!Main.getCommandHandler().getCommandNames().contains(args[1].toLowerCase()))
                    {
                        return "**" + args[1] + "** is not a valid command name! Make sure to not include the prefix!";
                    }
                    if(guildSettings.getUnrestrictedCommands().contains(args[1]))
                    {
                        return "**" + args[1] + "** is already unrestricted!";
                    }
                    break;

                case "p":
                case "prefix":
                    if(args.length < 2)
                    {
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " prefix [arg]`` where ``[arg]`` " +
                                "is the new command prefix for me on this guild.";
                    }
                    if(prefix.length() > 30)
                    {
                        return "Your custom prefix is to long! Your prefix can be at most 30 characters.";
                    }
                    break;

                case "c":
                case "control":
                    if(args.length < 2)
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " control [#channel]`` where ``[#channel]`` " +
                                "is the channel to use as the bot control channel.";
                    String chanId = args[1].replace("<#", "").replace(">", "");
                    try
                    {
                        Channel chan = event.getGuild().getTextChannelById(chanId);
                        if(chan == null)
                            return "I could not find the channel **" + args[1] + "** on your guild server!";
                    }
                    catch(NumberFormatException e)
                    {
                        return "I could not find the channel **" + args[1] + "** on your guild server!";
                    }
                    break;

                default:
                    return "**" + args[0] + "** is not a valid option!\n" +
                            "The correct usage is ``" + head + " [option] [arg]`` where ``[option]`` can be " +
                            "``prefix``, ``control``, ``restrict``, or ``unrestrict``.";

            }
        }

        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        try
        {
            GuildSettings guildSettings = Main.getGuildSettingsManager().getGuildSettings(event.getGuild().getId());

            if(args.length > 0)
            {
                ArrayList<String> commands;
                switch(args[0])
                {
                    case "r":
                    case "restrict":
                        commands = guildSettings.getUnrestrictedCommands();
                        commands.remove(args[1]);
                        guildSettings.setUnrestrictedCommands(commands);
                        break;

                    case "u":
                    case "unrestrict":
                        commands = guildSettings.getUnrestrictedCommands();
                        commands.add(args[1]);
                        guildSettings.setUnrestrictedCommands(commands);
                        break;

                    case "p":
                    case "prefix":
                        guildSettings.setPrefix(args[1]);
                        break;

                    case "c":
                    case "control":
                        String trimmed = args[1].replace("<#", "").replace(">","");
                        guildSettings.setCommandChannelId(trimmed);
                        break;
                }
            }

            // send settings message
            JDA.ShardInfo shardInfo = event.getJDA().getShardInfo();
            String body = "```js\n" +
                    "// Shard-" + shardInfo.getShardId() + " of " + shardInfo.getShardTotal() + "\n" +
                    "// Guild Settings\n" +
                    "[prefix]  \"" + guildSettings.getPrefix() + "\"\n";

            if(guildSettings.getCommandChannelId() != null)
            {
                body += "[control] \"#" + event.getGuild().getTextChannelById(guildSettings.getCommandChannelId()).getName() + "\"";
            }
            else
            {
                body += "[control] \"#" + Main.getBotSettingsManager().getControlChan() + "\" (default)";
            }

            body += "``````js\n// Command Settings\nRestricted Commands:\n";
            for(String command : guildSettings.getRestrictedCommands())
            {
                body += "\"" + command + "\" ";
            }
            body += "\nUnrestricted commands:\n";
            for(String command : guildSettings.getUnrestrictedCommands())
            {
                body += "\"" + command + "\" ";
            }
            body += "```";
            MessageUtilities.sendMsg(body, event.getChannel(), null);
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
