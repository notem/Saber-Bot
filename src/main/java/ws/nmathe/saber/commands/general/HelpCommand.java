package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Collection;

/**
 * the command which causes the bot to message the event's parent user with
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
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "To get detailed information concerning the usage of any of these" +
                " commands use the command ``" + head + " <command>`` where the prefix for <command> is stripped off.\n\n" +
                "Ex. ``" + head + " create``";

        String USAGE_BRIEF = "``" + head + "`` - receive help message";

        if( brief ) return USAGE_BRIEF;
        else return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        if(args.length>1)
        {
            return "That's too many arguments!";
        }
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        String INTRO = "```diff\n- Intro```\nI am **" + Main.getShardManager().getJDA().getSelfUser().getName() + "**, the event scheduling discord bot." +
                " I can provide your discord with basic event schedule management.\nInvite me to your discord and create " +
                "a dedicated command channel named **" + Main.getBotSettingsManager().getControlChan() + "** to get started.\n\n" +

                "github: <https://github.com/notem/Saber-Bot>\n" +
                "userdocs: <https://nmathe.ws/bots/saber>\n" +
                "support: <https://discord.gg/ZQZnXsC>\n" +
                "invite: <https://discordapp.com/api/oauth2/authorize?client_id=" + Main.getShardManager().getJDA().getSelfUser().getId() +
                "&scope=bot&permissions=523344>\n\n";

        String head = prefix + this.name();

        String USAGE_EXTENDED = "To get detailed information concerning the usage of any of these" +
                " commands use the command ``" + head + " <command>`` where the prefix for <command> is stripped off.\n\n" +
                "Ex. ``" + head + " create``";

        Collection<Command> commands = Main.getCommandHandler().getCommands();

        if(args.length < 1)     // send the bot intro with a brief list of commands to the user
        {
            String commandsBrief = ""; for( Command cmd : commands )
                commandsBrief += cmd.help(prefix, true) + "\n";

            MessageUtilities.sendPrivateMsg( INTRO + "```diff\n- Command List```\n" +
                    commandsBrief + "\n" + USAGE_EXTENDED, event.getAuthor(), null );
        }
        else    // otherwise read search the commands for the first arg
        {
            Command cmd = Main.getCommandHandler().getCommand( args[0] );
            if( cmd != null )
            {
                String helpMsg = cmd.help(prefix, false);
                if(helpMsg.length() > 1900)
                {
                    String[] split = helpMsg.split("splithere");
                    for(int i=0; i<split.length; i++)
                    {
                        MessageUtilities.sendPrivateMsg(split[i], event.getAuthor(), null);
                    }
                }
                else
                {
                    MessageUtilities.sendPrivateMsg(helpMsg, event.getAuthor(), null);
                }
            }
        }
    }
}
