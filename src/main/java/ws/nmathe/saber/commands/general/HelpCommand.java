package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Collection;

/**
 * the command which causes the bot to message the event's parent user with
 * the bot operation command list/guide. Attempts to remove the '!help' message
 * if the message does not originate from a private channel
 */
public class HelpCommand implements Command
{
    private CommandHandler cmdHandler = Main.getCommandHandler();
    private String prefix = Main.getBotSettings().getCommandPrefix();

    private String INTRO = "I am **" + Main.getBotSelfUser().getName() + "**, the task scheduling discord bot." +
            " I can provide your discord with basic event schedule management.  Invite me to your discord and set up " +
            "my appropriate channels to get started.\n\n" +
            "github: <https://github.com/notem/Saber-Bot>\n" +
            "userdocs: <https://nmathe.ws/bots/saber>\n" +
            "support server: <https://discord.gg/ZQZnXsC>\n\n";

    private String USAGE_EXTENDED = "To get detailed information concerning the usage of any of these" +
            " commands use the command ``" + prefix + "help <command>`` where the prefix for <command> is stripped off.\n" +
            "Ex. ``" + prefix + "help create``";

    private String USAGE_BRIEF = "``" + prefix + "help`` - Messages the user help messages.";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if(args.length>1)
            return "Too many arguments";
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Collection<Command> commands = cmdHandler.getCommands();

        // send the bot intro with a brief list of commands to the user
        if(args.length < 1)
        {
            String commandsBrief = ""; for( Command cmd : commands )
                commandsBrief += cmd.help( true ) + "\n";

            MessageUtilities.sendPrivateMsg( INTRO + "__**Available commands**__\n" +
                    commandsBrief + USAGE_EXTENDED, event.getAuthor(), null );
        }
        // otherwise read search the commands for the first arg
        else
        {
            Command cmd = cmdHandler.getCommand( args[0] );
            if( cmd != null )
            {
                String helpMsg = cmd.help(false);
                MessageUtilities.sendPrivateMsg(helpMsg, event.getAuthor(), null);
            }
        }

        if( !event.isFromType(ChannelType.PRIVATE) )
            MessageUtilities.deleteMsg( event.getMessage(), null );
    }
}
