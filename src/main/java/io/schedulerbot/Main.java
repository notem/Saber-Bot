package io.schedulerbot;

import io.schedulerbot.commands.*;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.Event;

import java.time.LocalTime;
import java.util.HashMap;

/**
 *  file: Main.java
 *  main class. initializes the bot
 */
public class Main {

    public static JDA jda;     // the JDA bot object
    private static HashMap<String, Command> commands = new HashMap<String, Command>();  // mapping of keywords to commands
    public static HashMap<Integer, EventEntryParser.EventEntry> schedule =
            new HashMap<Integer, EventEntryParser.EventEntry>();

    public static final CommandParser commandParser = new CommandParser();     // parser object used by MessageListener
    public static final EventEntryParser eventEntryParser = new EventEntryParser();

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
        }
        // if bot fails to initialize and start
        catch( Exception e )
        {
            e.printStackTrace();
        }

        // finish init by adding bot commands to the commands HashMap
        commands.put("help", new HelpCommand());
        commands.put("announce", new AnnounceCommand());
        commands.put("create", new CreateCommand());
        commands.put("destroy", new DestroyCommand());
        commands.put("edit", new EditCommand());
    }

    /**
     * function that is called when the listener receives a MessageReceivedEvent
     * handles the execution of the command (if valid command)
     * @param cc CommandContainer object containing parsed input tokens
     */
    public static void handleCommand(CommandParser.CommandContainer cc)
    {
        // if the invoking command appears in commands
        if(commands.containsKey(cc.invoke))
        {
            boolean valid = commands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if(valid)
                commands.get(cc.invoke).action(cc.args, cc.event);
            // otherwise send error message
            else
            {
                String msg = "Invalid arguments for: \"" + BotConfig.PREFIX + cc.invoke +"\"";
                cc.event.getChannel().sendMessage( msg ).queue();
            }
        }
        // else the invoking command is invalid
        else
        {
            String msg = "Invalid command: \"" + BotConfig.PREFIX + cc.invoke + "\"";
            cc.event.getChannel().sendMessage( msg ).queue();
        }
    }

    /**
     * Called when bot receives a message in EVENT_CHAN from itself
     * @param se the EventEntry object containing an active thread
     */
    public static void handleEventEntry(EventEntryParser.EventEntry se)
    {
        // put the EventEntry thread into a HashMap by ID
        schedule.put(se.eID, se);
    }

    /**
     *
     * @param e
     * @param event
     */
    public static void handleException( Exception e, Event event )
    {
        String err = "[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                + LocalTime.now().getSecond() + "]" + e.getLocalizedMessage();
        System.out.print( err );
    }

    /**
     *
     * @return
     */
    public static Integer newID()
    {
        Integer ID = (int) Math.ceil( Math.random() * (Math.pow(2,16) - 1) );
        while( schedule.containsKey( ID ) )
        {
            ID = (int) Math.ceil( Math.random() * (Math.pow(2,16) - 1) );
        }

        return ID;
    }
}
