package ws.nmathe.saber.core.command;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message.Interaction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.admin.*;
import ws.nmathe.saber.core.RateLimiter;
import ws.nmathe.saber.utils.MessageUtilities;
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
    private final ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("CommandHandler-%d").build()
    ); // thread pool for running commands
    private final RateLimiter rateLimiter = new RateLimiter();
    private final HashMap<String, Command> commands;         // maps Command to invoke string
    private final HashMap<String, Command> adminCommands;    // ^^ but for admin commands
    public final String argName = "args";
    public final String argString = "required command arguments";
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

        initialized = true;
    }

    public void updateCommands(JDA jda)
    {
        jda.updateCommands()
        .addCommands(Commands.slash("help", "DM the user bot info")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("init", "Create a schedule")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("create", "Add event to schedule")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("delete", "Delete an event")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("edit", "Modify an event")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("config", "Configure a schedule")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("timezones", "List valid timezones")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("test", "Test event announcement")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("sort", "Sort events on schedule")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("sync", "Synchronize schedule with Google Calendar")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("events", "List all events")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("schedules", "List all schedules")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("announcements", "List upcoming announcements")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("guild", "Configure guild options")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("skip", "Skip an event")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("list", "Show member RSVPs")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("manage", "Manage member RSVPs")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("purge", "Bulk delete messages")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .addCommands(Commands.slash("diagnose", "Debug permision issues")
                        .addOption(OptionType.STRING, this.argName, this.argString)
                    )
        .queue();
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
        if (!initialized)
        {
            String msg = "I have not yet finished booting up! Please try again in a moment.";
            if (event.isFromType(ChannelType.PRIVATE))
            {   // send message to DM channel
                MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
            }
            else
            {   // send message to channel the message was received on
                MessageUtilities.sendMsg(msg, event.getChannel(), null);
            }
            return;
        }

        // otherwise handle the received command
        CommandParser.CommandContainer cc = commandParser.parse(event, prefix);
        if (type == 0)
        {
            String identifier = event.getAuthor().getId();
            if (event.isFromType(ChannelType.TEXT))
                identifier +=  event.getGuild().getId();
            if (rateLimiter.check(identifier))
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
            handleGeneralCommand(cc, null);
        }
        else if (type == 1)
        {
            handleAdminCommand(cc);
        }
    }

    /**
     * Processes a MessageReceivedEvent into a command using the command parser
     * and executes the command
     * @param event (MessageReceivedEvent) containing the command
     * @param type (Integer) the type of command, 0 for public, 1 for admin
     * @param prefix (String) the prefix of the command (depends on guild)
     */
    public void handleCommand(SlashCommandInteractionEvent event, Integer type, String prefix)
    {
        SlashCommandInteraction interaction = event.getInteraction();

        // if the command handler has not yet been initialized, send a special error
        if (!initialized)
        {
            String msg = "I have not yet finished booting up! Please try again in a moment.";
            interaction.reply(msg).queue();
            return;
        }

        // otherwise handle the received command
        CommandParser.CommandContainer cc = commandParser.parse(event, prefix);
        if (type == 0)
        {
            String identifier = event.getUser().getId();
            if (event.getChannel().getType().equals(ChannelType.TEXT))
                identifier +=  event.getGuild().getId();
            if (rateLimiter.check(identifier))
            {
                String userMsg = "You have hit the ratelimit, please wait a few minutes before retrying your command.";
                interaction.reply(userMsg).queue();;

                String alert;
                if (event.getChannelType().equals(ChannelType.PRIVATE))
                {
                    alert = "@" + event.getUser().getName() +
                            " [" + event.getUser().getId() + "] was rate limited using the '" +
                            cc.invoke + "' command via DM!";
                }
                else
                {
                    alert = "@" + event.getUser().getName() +
                            " [" + event.getUser().getId() + "] was rate limited on '" +
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
            handleGeneralCommand(cc, interaction.deferReply());
        }
        else if (type == 1)
        {
            handleAdminCommand(cc);
        }
    }

    /**
     * Executes a public/general command
     * @param cc (CommandContainer) holding the command information
     */
    private void handleGeneralCommand(CommandParser.CommandContainer cc, ReplyCallbackAction action)
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

                            String info = "Executed command [" + cc.raw +
                                    "] by " + cc.event.getAuthor().getName() + " [" + cc.event.getAuthor().getId()+ "]";
                            if(cc.event.getGuild() != null)
                                info += " on " + cc.event.getGuild().getName()+ " [" + cc.event.getGuild().getId() + "]";
                            Logging.cmd(this.getClass(), info);
                        }
                        catch(Exception e)
                        {
                            Logging.exception(commands.get(cc.invoke).getClass(), e);
                        }
                        String sucString = "Completed execution of " + cc.invoke + " command.";
                        action.addContent(sucString).queue();
                    });
                }
                // otherwise send error message
                else
                {
                    String msg = "**Error** : " + err;
                    action.addContent(msg).queue();
                    //MessageUtilities.sendMsg(msg, cc.event.getChannel(), null);
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
            action.addContent(msg).queue();
            //MessageUtilities.sendMsg(msg, cc.event.getChannel(), null);
        }
    }

    /**
     * Executes administrative commands
     * @param cc (CommandContainer) holding the command information
     */
    private void handleAdminCommand(CommandParser.CommandContainer cc)
    {
        // for admin commands
        if (adminCommands.containsKey(cc.invoke))
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
