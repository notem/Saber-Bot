package io.schedulerbot;

import io.schedulerbot.commands.Command;
import io.schedulerbot.commands.admin.*;
import io.schedulerbot.commands.general.*;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.SelfUser;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *  file: Main.java
 *  main class. initializes the bot
 */
public class Main
{
    // the bot's JDA object
    private static JDA jda;

    // hash tables of commands and admin commands
    private static final HashMap<String, Command> commands = new HashMap<>();
    private static final HashMap<String, Command> adminCommands = new HashMap<>();

    // hash table containing ALL currently active event entry threads
    private static final HashMap<Integer, EventEntry> entriesGlobal = new HashMap<>();

    // hash table which associates guilds with a list of the Id's of their active event entry threads
    private static final HashMap<String, ArrayList<Integer>> entriesByGuild = new HashMap<>();

    // parsers to read and analyze commands and event entries
    public static final CommandParser commandParser = new CommandParser();
    public static final ScheduleParser scheduleParser = new ScheduleParser();

    // executor service which runs the SchedulerChecker thread ever minute
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final Object scheduleLock = new Object();     // lock when modifying entry maps

    // cached thread pools for command and schedule threads; seemed fun, tell me if it's worse then I think
    public static final ExecutorService scheduleExec = Executors.newCachedThreadPool();
    private static final ExecutorService commandExec = Executors.newCachedThreadPool();


    public static void main( String[] args )
    {
        try
        {
            // build bot
            jda = new JDABuilder(AccountType.BOT)
                    .addListener(new MessageListener()) // attach listener
                    .setToken(BotConfig.TOKEN)          // set token
                    .buildBlocking();
            // enable reconnect
            jda.setAutoReconnect(true);

            // set the bot's 'game' message
            jda.getPresence().setGame(new Game()
            {
                @Override
                public String getName()
                {
                    return "pm me " + BotConfig.PREFIX + "help or " + BotConfig.PREFIX + "setup";
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
        { e.printStackTrace(); }

        // add bot commands with their lookup name
        commands.put("help", new HelpCommand());
        commands.put("announce", new AnnounceCommand());
        commands.put("create", new CreateCommand());
        commands.put("destroy", new DestroyCommand());
        commands.put("edit", new EditCommand());
        commands.put("setup", new SetupCommand());

        // add administrator commands with their lookup name
        adminCommands.put("gannounce", new GlobalAnnounceCommand());
        adminCommands.put("query", new QueryCommand());

        // start the scheduler
        scheduler.scheduleAtFixedRate( new ScheduleChecker( entriesGlobal ),
                60 - (LocalTime.now().toSecondOfDay()%60), 60, TimeUnit.SECONDS );
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
                commandExec.submit( () -> {
                    commands.get(cc.invoke).action(cc.args, cc.event);
                });
            }
            // otherwise send error message
            else
            {
                String msg = "Invalid arguments for: \"" + BotConfig.PREFIX + cc.invoke +"\"";
                MessageUtilities.sendMsg( msg, cc.event.getChannel(), null );
            }
        }
        // else the invoking command is invalid
        else
        {
            String msg = "Invalid command: \"" + BotConfig.PREFIX + cc.invoke + "\"";
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
                commandExec.submit( () -> {
                    adminCommands.get(cc.invoke).action(cc.args, cc.event);
                });
            }
            else
            {
                String msg = "Invalid arguments for: \"" + BotConfig.PREFIX + "help\"";
                MessageUtilities.sendPrivateMsg( msg, cc.event.getAuthor(), null );
            }
        }
    }

    /**
     * Called when bot receives a message in EVENT_CHAN from itself
     *
     * @param se the EventEntry object containing an active thread
     * @param guildId the Id string of the guild to which the entry belongs
     */
    public static void handleEventEntry(EventEntry se, String guildId)
    {
        // put the EventEntry thread into a HashMap by ID
        entriesGlobal.put(se.eID, se);

        if( !entriesByGuild.containsKey( guildId ) )
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.eID);
            entriesByGuild.put( guildId, entries );
        }
        else
            entriesByGuild.get( guildId ).add( se.eID );

        // adjusts the displayed time til timer (since a proper once is not set at creation)
        se.adjustTimer();
        __out.printOut( Main.class.getClass(), "Added entry #" + Integer.decode( "0x" + se.eID ) + " from guild #" + guildId + " to hashmaps." );
    }

    /**
     * removes an entry Id from the entriesByGuild and entriesGlobal hashmaps
     * this should only be called when an event entry thread is dieing
     *
     * @param eId the event entry Id number
     * @param gId the guild String Id
     */
    public static void removeId( Integer eId, String gId )
    {
        entriesByGuild.get(gId).remove(eId);

        if (entriesByGuild.get(gId).isEmpty())
            entriesByGuild.remove(gId);
        entriesGlobal.remove(eId);
        __out.printOut( Main.class.getClass(), "Removed entry #" + Integer.decode( "0x" + eId ) + " from guild #" + gId + " to hashmaps." );
    }

    /**
     * generates a new unique Id number for an event entry.
     * the function may be passed a specific Id, for which it will check it's availability
     * and return the Id number for reuse if available
     *
     * @param oldId the Id value of the previous iteration of the entry, null if no last iteration
     * @return the newly generate Id number to assign to an entry
     */
    public static Integer newId( Integer oldId )
    {
            Integer ID;
            if (oldId == null)
                ID = (int) Math.ceil(Math.random() * (Math.pow(2, 16) - 1));
            else
                ID = oldId;

            while (entriesGlobal.containsKey(ID))
            {
                ID = (int) Math.ceil(Math.random() * (Math.pow(2, 16) - 1));
            }


            return ID;
    }

    /**
     * retrieves an event entry if it exists
     *
     * @param eId the event entry Id number
     * @return event entry
     */
    public static EventEntry getEventEntry(Integer eId)
    {
        if( entriesGlobal.containsKey(eId) )
        {
            return entriesGlobal.get(eId);
        }
        else
        {
            return null;
        }
    }

    /**
     * retrieves the array list of entry Id belong to a guild
     *
     * @param gId the guild Id as a string
     * @return array list of event entry Id integers
     */
    public static ArrayList<Integer> getEntriesByGuild( String gId )
    {
        if( entriesByGuild.containsKey(gId) )
        {
            return entriesByGuild.get(gId);
        }
        else
        {
            return null;
        }
    }

    /**
     * get this bot's self user object
     *
     * @return the bot's SelfUser object
     */
    public static SelfUser getBotSelfUser()
    {
        return jda.getSelfUser();
    }


    public static JDA getBotJda()
    {
        return jda;
    }


    /**
     * gets all commands available to general users
     *
     * @return a collection of all available commands
     */
    public static Collection<Command> getCommands()
    {
        return commands.values();
    }


    /**
     * retrieves a Command by it's invoking string, if it exists
     *
     * @param invoke String that invokes a command
     * @return Command that is invoke by invoke, or null if no Command
     */
    public static Command getCommand( String invoke )
    {
        if( commands.containsKey(invoke) )
        {
            return commands.get(invoke);
        }
        else
        {
            return null;
        }
    }
}
