package io.schedulerbot;

import io.schedulerbot.core.*;
import io.schedulerbot.core.command.CommandHandler;
import io.schedulerbot.core.schedule.ScheduleManager;
import io.schedulerbot.core.settings.GuildSettingsManager;
import io.schedulerbot.utils.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.SelfUser;

/**
 *  initializes and maintains the bot
 *  main maintains the entry maps as well as the command and schedule thread pools
 */
public class Main
{
    private static JDA jda;                  // api
    private static BotSettings botSettings;     // global config botSettings

    public static final ScheduleManager scheduleManager = new ScheduleManager();
    public static final GuildSettingsManager guildSettingsManager = new GuildSettingsManager();
    public static final CommandHandler commandHandler = new CommandHandler();

    public static void main( String[] args )
    {
        // get or generate bot settings
        botSettings = BotSettings.init();
        if( botSettings == null )
        {
            __out.printOut(Main.class, "Created a new java properties file. Add your " +
                    "bot token to the file and restart the bot.\n");
            return;
        }

        // build the bot
        try
        {
            jda = new JDABuilder(AccountType.BOT)
                    .addListener(new EventListener()) // attach listener
                    .setToken(botSettings.getToken()) // set token
                    .buildBlocking();
            // enable reconnect
            jda.setAutoReconnect(true);

            // set the bot's 'game' message
            jda.getPresence().setGame(new Game()
            {
                @Override
                public String getName()
                {
                    return "pm me " + botSettings.getCommandPrefix() + "help or " + botSettings.getCommandPrefix() + "setup";
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
        catch( Exception e )
        {
            e.printStackTrace();
            return;
        }

        commandHandler.init();
        scheduleManager.init();
    }

    public static SelfUser getBotSelfUser()
    {
        return jda.getSelfUser();
    }

    public static JDA getBotJda()
    {
        return jda;
    }

    public static BotSettings getBotSettings()
    {
        return botSettings;
    }
}
