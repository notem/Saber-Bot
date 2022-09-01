package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.settings.GuildSettingsManager.GuildSettings;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.util.ArrayList;

/**
 * used for viewing and setting guild settings
 */
public class GuildCommand implements Command
{
    @Override
    public String name()
    {
        return "guild";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - adjust guild-wide settings";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.CORE);

        String cat1 = "- Usage\n" + head + " <option> <new config>";
        String cont1 = "The guild command may be used to modify guild-wide settings such as your guild's command prefix and control channel.\n" +
                        "Issuing the guild command without arguments will output the current settings for your discord server.\n\n" +
                        "Options for <option> are: ``restrict``, ``unrestrict``, ``prefix``, and ``control``.";
        info.addUsageCategory(cat1, cont1);

        String cat2 = "+ (Un)Restricted Commands";
        String cont2 = "Commands may be configured as either 'restricted' or 'unrestricted' using the ``restrict`` and ``unrestrict`` command options.\n\n" +
                        "A restricted command can only be used in the designated control channel.\n" +
                        "Unrestricted commands may be used in any channel the bot is allowed to view, and by anyone who can post in those channels.";
        info.addUsageCategory(cat2, cont2);

        info.addUsageExample(head+" control #bot_management");
        info.addUsageExample(head+" unrestrict info");
        info.addUsageExample(head+" restrict create");
        info.addUsageExample(head+" prefix $");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
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
                        return "**" + args[1] + "** is not a valid command name! Be sure to not include the prefix!";
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
                        return "**" + args[1] + "** is not a valid command name! Be sure to not include the prefix!";
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
                    {
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " control [#channel]`` where ``[#channel]`` " +
                                "is the channel to use as the bot control channel.";
                    }
                    if(!args[1].matches("<#\\d+>"))
                    {
                        return "**" + args[1] + "** does not look like a #channel to me!\n" +
                                "Please use the usual discord channel notation!";
                    }
                    String chanId = args[1].replaceAll("[^\\d]", "");
                    try
                    {
                        MessageChannel chan = event.getGuild().getTextChannelById(chanId);
                        if(chan == null)
                        {
                            return "I could not find the channel **" + args[1] + "** on your guild server!";
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        return "I could not find the channel **" + args[1] + "** on your guild server!";
                    }
                    break;

                case "l":
                case "late":
                    if (args.length < 2)
                    {
                        return "That's not enough arguments!\n" +
                                "The correct usage is ``" + head + " late [minutes]`` where ``[minutes]`` " +
                                "is the number of minutes late an announcement is allowed to be before it is discarded.";
                    }
                    if(!VerifyUtilities.verifyInteger(args[1]))
                    {
                        return "**"+args[1]+"** should be a number!";
                    }
                    if(Integer.parseInt(args[1]) <= 0)
                    {
                        return "The late threshold must be a positive number!";
                    }
                    if(Integer.parseInt(args[1]) > 24*60)
                    {
                        return "The late threshold cannot be greater than 24 hours!";
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
    public void action(String head, String[] args, EventCompat event)
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
                    String trimmed = args[1].replaceAll("[^\\d]", "");
                    guildSettings.setCommandChannelId(trimmed);
                    break;

                case "l":
                case "late":
                    Integer va = Integer.parseInt(args[1].replaceAll("[^\\d]", ""));
                    guildSettings.setLateThreshold(va);
                    break;
            }
        }

        // send settings message
        JDA.ShardInfo shardInfo = event.getJDA().getShardInfo();
        String body = "```js\n" +
                (shardInfo!=null?("// Shard-" + shardInfo.getShardId() + " of " + shardInfo.getShardTotal() + "\n"):"") +
                "// Guild Settings\n" +
                "[prefix]  \"" + guildSettings.getPrefix() + "\"\n" +
                "[late]    \"" + guildSettings.getLateThreshold() + "\"\n";

        if(guildSettings.getCommandChannelId() != null)
        {
            String control = event.getGuild().getTextChannelById(guildSettings.getCommandChannelId()).getName();
            body += "[control] \"#" + control + "\"";
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
}
