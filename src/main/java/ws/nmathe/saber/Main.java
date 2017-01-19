package ws.nmathe.saber;

import net.dv8tion.jda.core.entities.Guild;
import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.BotSettings;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.SelfUser;
import ws.nmathe.saber.core.EventListener;
import ws.nmathe.saber.utils.GuildUtilities;
import ws.nmathe.saber.utils.HttpUtilities;
import ws.nmathe.saber.utils.__out;

/**
 *  initializes and maintains the bot
 *  main maintains the entry maps as well as the command and schedule thread pools
 */
public class Main
{
    private static JDA jda;                     // api
    private static BotSettings botSettings;     // global config botSettings

    private static ScheduleManager scheduleManager = new ScheduleManager();
    private static ChannelSettingsManager channelSettingsManager = new ChannelSettingsManager();
    private static CommandHandler commandHandler = new CommandHandler();

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
                    .setToken(botSettings.getToken()) // set token
                    .buildBlocking();
            // attach listener
            jda.addEventListener(new EventListener());
            // enable reconnect
            jda.setAutoReconnect(true);

            // set the bot's 'game' message
            jda.getPresence().setGame(new Game()
            {
                @Override
                public String getName()
                {
                    return "Schedule Bot | " + botSettings.getCommandPrefix() + "help " + botSettings.getCommandPrefix() + "setup";
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

        // load schedule channels
        for (Guild guild : jda.getGuilds())
        {
            GuildUtilities.loadScheduleChannels( guild );
        }

        commandHandler.init();      // ready commands
        scheduleManager.init();     // start timers
        GoogleAuth.init();          // ready the gCal service

        HttpUtilities.updateStats();
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

    public static CommandHandler getCommandHandler()
    {
        return commandHandler;
    }

    public static ScheduleManager getScheduleManager()
    {
        return scheduleManager;
    }

    public static ChannelSettingsManager getChannelSettingsManager()
    {
       return channelSettingsManager;
    }
}
