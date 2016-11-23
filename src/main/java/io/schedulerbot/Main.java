package io.schedulerbot;

import io.schedulerbot.commands.AnnounceCommand;
import io.schedulerbot.commands.Command;
import io.schedulerbot.commands.CreateCommand;
import io.schedulerbot.commands.HelpCommand;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

import java.time.LocalTime;
import java.util.HashMap;

/**
 *  file: Main.java
 *  main class. initializes the bot
 */
public class Main {

    public static JDA jda;     // the JDA bot object
    private static HashMap<String, Command> commands = new HashMap<String, Command>();  // mapping of keywords to commands
    public static HashMap<Integer, ScheduleParser.ScheduledEvent> schedule =
            new HashMap<Integer, ScheduleParser.ScheduledEvent>();

    public static final CommandParser commandParser = new CommandParser();     // parser object used by MessageListener
    public static final ScheduleParser scheduleParser = new ScheduleParser();

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
     * @param se
     */
    public static void handleScheduledEvent(ScheduleParser.ScheduledEvent se)
    {
        // put the ScheduledEvent thread into a HashMap by ID
        schedule.put(se.eventID, se);
    }

    public static void handleException( Exception e )
    {
        String err = "[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                + LocalTime.now().getSecond() + "]" + e.getLocalizedMessage();
        System.out.print( err );
    }
}
