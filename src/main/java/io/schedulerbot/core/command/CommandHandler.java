package io.schedulerbot.core.command;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.commands.general.*;
import io.schedulerbot.commands.admin.*;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 */
public class CommandHandler
{
    private final CommandParser commandParser = new CommandParser();      // parses command strings into containers
    private final ExecutorService commandExec = Executors.newCachedThreadPool(); // thread pool for running commands
    private HashMap<String, Command> commands;         // maps Command to invoke string
    private HashMap<String, Command> adminCommands;    // ^^ but for admin commands

    public CommandHandler()
    {
        commands = new HashMap<>();
        adminCommands = new HashMap<>();
    }

    public void init()
    {
        // add bot commands with their lookup name
        commands.put("help", new HelpCommand());
        commands.put("announce", new AnnounceCommand());
        commands.put("create", new CreateCommand());
        commands.put("destroy", new DestroyCommand());
        commands.put("edit", new EditCommand());
        commands.put("setup", new SetupCommand());
        commands.put("set", new SetCommand());
        commands.put("timezones", new TimeZonesCommand());

        // add administrator commands with their lookup name
        adminCommands.put("global_announce", new GlobalAnnounceCommand());
        adminCommands.put("query", new QueryCommand());
        adminCommands.put("update_all", new UpdateAllCommand());
        adminCommands.put("stats", new StatsCommand());
    }

    public void handleCommand( MessageReceivedEvent event, Integer type )
    {
        CommandParser.CommandContainer cc = commandParser.parse( event );
        if( type == 0 )
        {
            handleGeneralCommand( cc );
        }
        else if( type == 1 )
        {
            handleAdminCommand( cc );
        }

    }

    private void handleGeneralCommand(CommandParser.CommandContainer cc)
    {
        // if the invoking command appears in commands
        if(commands.containsKey(cc.invoke))
        {
            String err = commands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if(err.equals(""))
            {
                commandExec.submit( () -> commands.get(cc.invoke).action(cc.args, cc.event));
            }
            // otherwise send error message
            else
            {
                String msg = "Error : " + err;
                MessageUtilities.sendMsg( msg, cc.event.getChannel(), null );
            }
        }
        // else the invoking command is invalid
        else
        {
            String msg = "Invalid command \"" + Main.getBotSettings().getCommandPrefix() + cc.invoke + "\"";
            MessageUtilities.sendMsg( msg, cc.event.getChannel(), null );
        }
    }

    private void handleAdminCommand(CommandParser.CommandContainer cc)
    {
        // for admin commands
        if(adminCommands.containsKey(cc.invoke))
        {
            String err = adminCommands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if (err.equals(""))
            {
                commandExec.submit( () -> adminCommands.get(cc.invoke).action(cc.args, cc.event));
            }
        }
    }

    public Collection<Command> getCommands()
    {
        return commands.values();
    }

    public Command getCommand( String invoke )
    {
        // check if command exists, if so return it
        if( commands.containsKey(invoke) )
            return commands.get(invoke);

        else    // otherwise return null
            return null;
    }

}
