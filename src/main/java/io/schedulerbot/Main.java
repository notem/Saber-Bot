package io.schedulerbot;

import io.schedulerbot.commands.*;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *  file: Main.java
 *  main class. initializes the bot
 */
public class Main {

    public static JDA jda;     // the JDA bot object
    public static HashMap<String, Command> commands = new HashMap<>();  // mapping of keywords to commands
    public static HashMap<String, Command> adminCommands = new HashMap<>();
    public static final HashMap<Integer, EventEntryParser.EventEntry> entriesGlobal = new HashMap<Integer, EventEntryParser.EventEntry>();
    public static HashMap<String, ArrayList<Integer>> entriesByGuild = new HashMap<>();

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
                sendMsg( msg, cc.event.getChannel() );
            }
        }
        // else the invoking command is invalid
        else
        {
            String msg = "Invalid command: \"" + BotConfig.PREFIX + cc.invoke + "\"";
            sendMsg( msg, cc.event.getChannel() );
        }
    }

    public static void handlePrivateCommand(CommandParser.CommandContainer cc)
    {
        if(cc.invoke.equals("help"))
        {
            boolean valid = commands.get("help").verify(cc.args, cc.event);

            // do command action if valid arguments
            if(valid)
                commands.get("help").action(cc.args, cc.event);
                // otherwise send error message
            else
            {
                String msg = "Invalid arguments for: \"" + BotConfig.PREFIX + "help\"";
                sendPrivateMsg( msg, cc.event.getAuthor() );
            }
        }
        // for admin commands
        else if(adminCommands.containsKey(cc.invoke))
        {
            boolean valid = commands.get(cc.invoke).verify(cc.args, cc.event);

            // do command action if valid arguments
            if (valid)
                commands.get(cc.invoke).action(cc.args, cc.event);
            else
            {
                String msg = "Invalid arguments for: \"" + BotConfig.PREFIX + "help\"";
                sendPrivateMsg( msg, cc.event.getAuthor() );
            }
        }
    }

    /**
     * Called when bot receives a message in EVENT_CHAN from itself
     * @param se the EventEntry object containing an active thread
     */
    public static void handleEventEntry(EventEntryParser.EventEntry se, String guildId)
    {
        // put the EventEntry thread into a HashMap by ID
        entriesGlobal.put(se.eID, se);

        if( !entriesByGuild.containsKey( guildId ) )
        {
            ArrayList<Integer> entries = new ArrayList<Integer>();
            entries.add(se.eID);
            entriesByGuild.put( guildId, entries );
        }
        else
            entriesByGuild.get( guildId ).add( se.eID );
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

    public static void removeId( Integer eId, String guildId )
    {
        entriesByGuild.get( guildId ).remove( eId );

        if( entriesByGuild.get( guildId ).isEmpty() )
            entriesByGuild.remove( guildId );

        entriesGlobal.remove( eId );
    }

    public static Integer newId( Integer oldID )
    {
        Integer ID;
        if( oldID==null )
            ID = (int) Math.ceil( Math.random() * (Math.pow(2,16) - 1) );
        else
            ID = oldID;

        while( entriesGlobal.containsKey( ID ) )
        {
            ID = (int) Math.ceil( Math.random() * (Math.pow(2,16) - 1) );
        }

        return ID;
    }

    /**
     *
     * @param msg
     * @param chan
     */
    public static void sendMsg( String msg, MessageChannel chan )
    {
        try
        {
            chan.sendMessage(msg).queue();
        }
        catch (Exception e)
        {
            Main.handleException(e, null);
        }
    }

    public static void sendPrivateMsg( String msg, User user )
    {
        try
        {
            user.openPrivateChannel();
            sendMsg( msg, user.getPrivateChannel() );
        }
        catch( Exception e )
        {

        }
    }

    public static void sendAnnounce( String msg, Guild guild )
    {
        if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
            Main.sendMsg(msg, guild.getPublicChannel());

        else
            Main.sendMsg(msg, guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0));
    }

    public static void editMsg( String msgStr, Message msg )
    {
        try
        {
            msg.editMessage(msgStr).queue();
        }
        catch( Exception e )
        {
            return;
        }
    }

    /**
     *
     * @param msg
     */
    public static void deleteMsg( Message msg )
    {
        try
        {
            msg.deleteMessage().queue();
        }
        catch( PermissionException e )
        {
            Main.handleException( e, null );
        }
    }
}
