package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * the command which causes the bot to message the user with
 * the bot operation command list/guide.
 */
public class HelpCommand implements Command
{
    @Override
    public String name()
    {
        return "help";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - receive info messages";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.USER);

        String cat1 = "- Usage\n"+head+" [<command name>]";
        String cont1 = "This commands provides access to the command help documentation.\n" +
                "To get detailed information concerning the usage of any particular" +
                " commands use the command ``" + head + " <command>`` where the prefix for <command> is stripped off.\n\n";
        info.addUsageCategory(cat1, cont1);

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        // no arguments
        // send the bot intro with a brief list of commands to the user
        if(args.length < 1)
        {
            String intro = "```diff\n- Intro```\nI am **" + Main.getShardManager().getJDA().getSelfUser().getName() + "**, the event scheduling discord bot." +
                    " I can provide your discord with basic event schedule management.\nInvite me to your discord and create " +
                    "a dedicated command channel named **" + Main.getBotSettingsManager().getControlChan() + "** to get started.\n\n" +

                    "github: <https://github.com/notem/Saber-Bot>\n" +
                    "support: <https://discord.gg/ZQZnXsC>\n" +
                    "invite: <https://discordapp.com/api/oauth2/authorize?client_id=" + Main.getShardManager().getJDA().getSelfUser().getId() +
                    "&scope=bot&permissions=523344>\n\n";

            // generate list of commands
            String commands = "```diff\n- Command List```\n";
            commands += "**Core commands**\n================\n";
            for(Command cmd : Main.getCommandHandler().getCommands())
            {
                CommandInfo info = cmd.info(prefix);
                if(info.getType()== CommandInfo.CommandType.CORE) commands += info.getUsage()+"\n";
            }
            commands += "\n**User commands**\n================\n";
            for(Command cmd : Main.getCommandHandler().getCommands())
            {
                CommandInfo info = cmd.info(prefix);
                if(info.getType()== CommandInfo.CommandType.USER) commands += info.getUsage()+"\n";
            }
            commands += "\n**Google commands**\n================\n";
            for(Command cmd : Main.getCommandHandler().getCommands())
            {
                CommandInfo info = cmd.info(prefix);
                if(info.getType()== CommandInfo.CommandType.GOOGLE) commands += info.getUsage()+"\n";
            }
            commands += "\n**Misc commands**\n================\n";
            for(Command cmd : Main.getCommandHandler().getCommands())
            {
                CommandInfo info = cmd.info(prefix);
                if(info.getType()== CommandInfo.CommandType.MISC) commands += info.getUsage()+"\n";
            }

            String boot = "\nTo view detailed information for any of the above commands, DM me ``help command``.";

            String msg = intro + commands + boot;
            MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
        }
        // generate help info for the requested command
        else
        {
            Command cmd = Main.getCommandHandler().getCommand(args[0]);
            if( cmd != null )
            {
                CommandInfo info = cmd.info(prefix);
                String msg = "";
                if(!info.getUsageExtended().keySet().isEmpty())
                {
                    for(String key : info.getUsageExtended().keySet())
                    {
                        msg += "```diff\n" + key + "```\n" + info.getUsageExtended().get(key) + "\n\n";
                        if(msg.length() > 1000)
                        {
                            MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                            msg = "";
                        }
                    }
                }
                if(!info.getUsageExamples().isEmpty())
                {
                    msg += "```diff\n- Examples```";
                    for(String example : info.getUsageExamples())
                    {
                        msg += "\n``" + example + "``";
                    }
                }
                MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
            }
            else
            {
                String msg = "There is no command called *"+args[0]+"*!";
                MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
            }
        }
    }
}
