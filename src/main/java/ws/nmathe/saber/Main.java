package ws.nmathe.saber;

import com.google.common.collect.Iterables;
import net.dv8tion.jda.core.OnlineStatus;
import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.core.database.Driver;
import ws.nmathe.saber.core.google.CalendarConverter;
import ws.nmathe.saber.core.schedule.EntryManager;
import ws.nmathe.saber.core.settings.BotSettingsManager;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import ws.nmathe.saber.core.EventListener;
import ws.nmathe.saber.core.settings.GuildSettingsManager;
import ws.nmathe.saber.utils.HttpUtilities;
import ws.nmathe.saber.utils.Logging;
import java.util.*;

/**
 *  initializes and maintains the bot
 *  main maintains the entry maps as well as the command and schedule thread pools
 */
public class Main
{
    private static JDA jda;

    private static BotSettingsManager botSettingsManager = new BotSettingsManager();
    private static EntryManager entryManager = new EntryManager();
    private static ScheduleManager scheduleManager = new ScheduleManager();
    private static CommandHandler commandHandler = new CommandHandler();
    private static CalendarConverter calendarConverter = new CalendarConverter();
    private static GuildSettingsManager guildSettingsManager = new GuildSettingsManager();
    private static Driver mongoDriver = new Driver();
    private static Iterator<String> games;

    public static void main( String[] args )
    {
        if( botSettingsManager.hasSettings() )
        {
            Logging.info(Main.class, "A 'saber.toml' configuration file has been created. Add your " +
                    "bot token to the file and restart the bot.\n");
            System.exit(0);
        }

        mongoDriver.init();         // ready database

        try // build the bot
        {
            jda = new JDABuilder(AccountType.BOT)
                    .setToken(botSettingsManager.getToken())
                    .setStatus(OnlineStatus.ONLINE)
                    .setCorePoolSize(20)
                    .buildBlocking();
            jda.addEventListener(new EventListener());
            jda.setAutoReconnect(true);

            // cycle "now playing" message every 30 seconds
            games = Iterables.cycle(botSettingsManager.getNowPlayingList()).iterator();
            (new Timer()).scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    jda.getPresence().setGame(new Game()
                    {
                        @Override
                        public String getName()
                        { return games.next(); }

                        @Override
                        public String getUrl()
                        { return "https://nmathe.ws/bots/saber"; }

                        @Override
                        public GameType getType()
                        { return GameType.DEFAULT; }
                    });
                }
            }, 0, 30*1000);
        }
        catch( Exception e )
        {
            Logging.exception(Main.class, e);
            System.exit(1);
        }

        calendarConverter.init();   // connect to calendar service
        entryManager.init();        // start timers
        commandHandler.init();      // ready commands

        HttpUtilities.updateStats();
    }


    public static JDA getBotJda()
    {
        return jda;
    }

    public static BotSettingsManager getBotSettingsManager()
    {
        return botSettingsManager;
    }

    public static CommandHandler getCommandHandler()
    {
        return commandHandler;
    }

    public static EntryManager getEntryManager()
    {
        return entryManager;
    }

    public static ScheduleManager getScheduleManager()
    {
       return scheduleManager;
    }

    public static GuildSettingsManager getGuildSettingsManager()
    {
        return guildSettingsManager;
    }

    public static CalendarConverter getCalendarConverter()
    {
        return calendarConverter;
    }

    public static Driver getDBDriver()
    {
        return mongoDriver;
    }

    public static void reloadNowPlayingList()
    {
        games = Iterables.cycle(botSettingsManager.getNowPlayingList()).iterator();
    }
}
