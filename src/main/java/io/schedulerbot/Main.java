package io.schedulerbot;

import io.schedulerbot.commands.Command;
import io.schedulerbot.commands.admin.*;
import io.schedulerbot.commands.general.*;
import io.schedulerbot.core.*;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.SelfUser;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *  initializes and maintains the bot
 *  main maintains the entry maps as well as the command and schedule thread pools
 */
public class Main
{
    public static JDA jda;                  // api
    public static BotSettings settings;     // global config settings

    public static final CommandParser commandParser = new CommandParser();      // parses command strings into containers
    private static HashMap<String, Command> commands = new HashMap<>();         // maps Command to invoke string
    private static HashMap<String, Command> adminCommands = new HashMap<>();    // ^^ but for admin commands
    public static final ExecutorService commandExec = Executors.newCachedThreadPool(); // thread pool for running commands

    public static final ScheduleManager scheduleManager = new ScheduleManager();
    public static final GuildSettingsManager guildSettingsManager = new GuildSettingsManager();

    public static void main( String[] args )
    {
        try
        {
            settings = BotSettings.init();
            if( settings == null )
            {
                __out.printOut(Main.class, "Created a new java properties file. Add your " +
                        "bot token to the file and restart the bot.\n");
                return;
            }

            // build bot
            jda = new JDABuilder(AccountType.BOT)
                    .addListener(new EventListener()) // attach listener
                    .setToken(settings.getToken())          // set token
                    .buildBlocking();
            // enable reconnect
            jda.setAutoReconnect(true);

            // set the bot's 'game' message
            jda.getPresence().setGame(new Game()
            {
                @Override
                public String getName()
                {
                    return "pm me " + settings.getCommandPrefix() + "help or " + settings.getCommandPrefix() + "setup";
                }

                @Override
                public String getUrl()
                {
                    return "";
                }

                @Override
                public GameType getType()
                {
                    return GameType.DEFAULT;
                }
            });
        }
        // if bot fails to initialize and start
        catch( Exception e )
        {
            __out.printOut( Main.class, e.getMessage() );
            return;
        }

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

        scheduleManager.startTimers();
    }

    /**
     * function that is called when the listener receives a MessageReceivedEvent
     * handles the execution of the command (if valid command)
     *
     * @param cc CommandContainer object containing parsed input tokens
     */
    public static void handleGeneralCommand(CommandParser.CommandContainer cc)
    {
        // if the invoking command appears in commands
        if(commands.containsKey(cc.invoke))
        {
            boolean valid = commands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if(valid)
            {
                commandExec.submit( () -> commands.get(cc.invoke).action(cc.args, cc.event));
            }
            // otherwise send error message
            else
            {
                String msg = "Invalid arguments for: \"" + settings.getCommandPrefix() + cc.invoke +"\"";
                MessageUtilities.sendMsg( msg, cc.event.getChannel(), null );
            }
        }
        // else the invoking command is invalid
        else
        {
            String msg = "Invalid command: \"" + settings.getCommandPrefix() + cc.invoke + "\"";
            MessageUtilities.sendMsg( msg, cc.event.getChannel(), null );
        }
    }

    /**
     * like handleCommand, except instead intended to be used for commands received in
     * private message.
     *
     * @param cc CommandContainer, the object that holds necessary items to call a command
     */
    public static void handleAdminCommand(CommandParser.CommandContainer cc)
    {
        // for admin commands
        if(adminCommands.containsKey(cc.invoke))
        {
            boolean valid = adminCommands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if (valid)
            {
                commandExec.submit( () -> adminCommands.get(cc.invoke).action(cc.args, cc.event));
            }
        }
    }

    public static SelfUser getBotSelfUser()
    {
        return jda.getSelfUser();
    }

    public static JDA getBotJda()
    {
        return jda;
    }

    public static Collection<Command> getCommands()
    {
        return commands.values();
    }

    public static Command getCommand( String invoke )
    {
        // check if command exists, if so return it
        if( commands.containsKey(invoke) )
            return commands.get(invoke);

        else    // otherwise return null
            return null;
    }

    public static BotSettings getSettings()
    {
        return settings;
    }
}
