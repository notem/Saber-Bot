package ws.nmathe.saber.core.command;

import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.User;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.admin.*;
import ws.nmathe.saber.core.RateLimiter;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.commands.general.*;
import ws.nmathe.saber.utils.Logging;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles MessageEvents which contain user commands
 */
public class CommandHandler
{
    private final CommandParser commandParser = new CommandParser();      // parses command strings into containers
    private final ExecutorService executor = Executors.newCachedThreadPool(); // thread pool for running commands
    private final RateLimiter rateLimiter = new RateLimiter();
    private final HashMap<String, Command> commands;         // maps Command to invoke string
    private final HashMap<String, Command> adminCommands;    // ^^ but for admin commands
    private boolean initialized;

    public CommandHandler()
    {
        commands = new HashMap<>();
        adminCommands = new HashMap<>();
        initialized = false;
    }

    /**
     * Loads all commands into the command table
     */
    public void init()
    {
        // add bot commands with their lookup name
        commands.put((new HelpCommand()).name(), new HelpCommand());
        commands.put((new InitCommand()).name(), new InitCommand());
        commands.put((new CreateCommand()).name(), new CreateCommand());
        commands.put((new DeleteCommand()).name(), new DeleteCommand());
        commands.put((new EditCommand()).name(), new EditCommand());
        commands.put((new ConfigCommand()).name(), new ConfigCommand());
        commands.put((new TimeZonesCommand()).name(), new TimeZonesCommand());
        commands.put((new TestCommand()).name(), new TestCommand());
        commands.put((new SortCommand()).name(), new SortCommand());
        commands.put((new ListCommand()).name(), new ListCommand());
        commands.put((new GuildCommand()).name(), new GuildCommand());
        commands.put((new OAuthCommand()).name(), new OAuthCommand());
        commands.put((new SkipCommand()).name(), new SkipCommand());
        commands.put((new EventsCommand()).name(), new EventsCommand());
        commands.put((new SchedulesCommand()).name(), new SchedulesCommand());
        commands.put((new AnnouncementsCommand()).name(), new AnnouncementsCommand());
        commands.put((new ManageCommand()).name(), new ManageCommand());
        commands.put((new PurgeCommand().name()), new PurgeCommand());
        commands.put((new DiagnoseCommand()).name(), new DiagnoseCommand());

        // add administrator commands with their lookup name
        adminCommands.put((new GlobalMsgCommand()).name(), new GlobalMsgCommand());
        adminCommands.put((new StatsCommand()).name(), new StatsCommand());
        adminCommands.put((new ReloadSettingsCommand()).name(), new ReloadSettingsCommand());
        adminCommands.put((new ClearLocksCommand()).name(), new ClearLocksCommand());
        adminCommands.put((new ShardsCommand()).name(), new ShardsCommand());
        adminCommands.put((new AvatarCommand()).name(), new AvatarCommand());

        initialized = true;
    }

    /**
     * Processes a MessageReceivedEvent into a command using the command parser
     * and executes the command
     * @param event (MessageReceivedEvent) containing the command
     * @param type (Integer) the type of command, 0 for public, 1 for admin
     * @param prefix (String) the prefix of the command (depends on guild)
     */
    public void handleCommand(MessageReceivedEvent event, Integer type, String prefix)
    {
        // if the command handler has not yet been initialized, send a special error
        if(!initialized)
        {
            String msg = "I have not yet finished booting up! Please try again in a moment.";
            if(event.isFromType(ChannelType.PRIVATE))
            {   // send message to DM channel
                MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
            }
            else
            {   // send message to channel the message was received on
                MessageUtilities.sendMsg(msg, event.getTextChannel(), null);
            }
            return;
        }

        // otherwise handle the received command
        CommandParser.CommandContainer cc = commandParser.parse(event, prefix);
        if (type == 0)
        {
            if (rateLimiter.check(event.getAuthor().getId()))
            {
                String alert;
                if (event.getChannelType().equals(ChannelType.PRIVATE))
                {
                    alert = "@" + event.getAuthor().getName() +
                            " [" + event.getAuthor().getId() + "] was rate limited using the '" +
                            cc.invoke + "' command via DM!";
                }
                else
                {
                    alert = "@" + event.getAuthor().getName() +
                            " [" + event.getAuthor().getId() + "] was rate limited on '" +
                            event.getGuild().getName() +"' [" + event.getGuild().getId() + "] using the '" +
                            cc.invoke + "' command!";
                }

                // alert admin
                Logging.warn(this.getClass(), alert);
                User admin = event.getJDA().getUserById(Main.getBotSettingsManager().getAdminId());
                if(admin != null)
                {
                    MessageUtilities.sendPrivateMsg(alert, admin, null);
                }
                return;
            }
            handleGeneralCommand(cc);
        }
        else if( type == 1 )
        {
            handleAdminCommand(cc);
        }

    }

    /**
     * Executes a public/general command
     * @param cc (CommandContainer) holding the command information
     */
    private void handleGeneralCommand(CommandParser.CommandContainer cc)
    {
        // if the invoking command appears in commands
        if(commands.containsKey(cc.invoke))
        {
            try // catch any errors which occur while parsing user input
            {
                String err = commands.get(cc.invoke).verify(cc.prefix, cc.args, cc.event);
                // do command action if valid arguments
                if(err.isEmpty())
                {
                    executor.submit( () ->
                    {
                        try
                        {
                            commands.get(cc.invoke).action(cc.prefix, cc.args, cc.event);

                            String info = "Executed command [" + cc.event.getMessage().getContentRaw() +
                                    "] by " + cc.event.getAuthor().getName() + " [" + cc.event.getMessage().getAuthor().getId()+ "]";
                            if(cc.event.getGuild() != null)
                                info += " on " + cc.event.getGuild().getName()+ " [" + cc.event.getGuild().getId() + "]";
                            Logging.cmd(this.getClass(), info);
                        }
                        catch(Exception e)
                        {
                            Logging.exception(commands.get(cc.invoke).getClass(), e);
                        }
                    });
                }
                // otherwise send error message
                else
                {
                    String msg = "**Error** : " + err;
                    MessageUtilities.sendMsg(msg, cc.event.getChannel(), null);
                }
            }
            catch(Exception e)
            {
                User admin = cc.event.getJDA().getUserById(Main.getBotSettingsManager().getAdminId());
                if(admin != null)
                {
                    MessageUtilities.sendPrivateMsg(e.toString(), admin, null);
                }
                Logging.exception(this.getClass(), e);
            }
        }
        else
        {   // command is not a valid command
            String msg = "**" + cc.invoke + "** is not a command!";
            MessageUtilities.sendMsg(msg, cc.event.getChannel(), null);
        }
    }

    /**
     * Executes administrative commands
     * @param cc (CommandContainer) holding the command information
     */
    private void handleAdminCommand(CommandParser.CommandContainer cc)
    {
        // for admin commands
        if(adminCommands.containsKey(cc.invoke))
        {
            try // catch any errors which occur while parsing user input
            {
                String err = adminCommands.get(cc.invoke).verify(cc.prefix + cc.invoke, cc.args, cc.event);

                // do command action if valid arguments
                if (err.equals(""))
                {
                    executor.submit( () ->
                    {
                        try
                        {
                            adminCommands.get(cc.invoke).action(cc.prefix + cc.invoke, cc.args, cc.event);
                        }
                        catch(Exception e)
                        {
                            Logging.exception(adminCommands.get(cc.invoke).getClass(), e);
                        }
                    });
                }
            }
            catch(Exception e)
            {
                User admin = cc.event.getJDA().getUserById(Main.getBotSettingsManager().getAdminId());
                if(admin != null)
                {
                    MessageUtilities.sendPrivateMsg(e.getLocalizedMessage(), admin, null);
                }
                Logging.exception(this.getClass(), e);
            }
        }
    }

    public Collection<Command> getCommands()
    {
        return commands.values();
    }

    public Collection<String> getCommandNames()
    {
        return commands.keySet();
    }

    public Command getCommand(String invoke)
    {
        // check if command exists, if so return it
        // otherwise return null
        return commands.getOrDefault(invoke, null);
    }

    public void putSync()
    {
        commands.put("sync", new SyncCommand());
    }
}
