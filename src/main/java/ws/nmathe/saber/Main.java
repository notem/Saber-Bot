package ws.nmathe.saber;

import ws.nmathe.saber.core.ShardManager;
import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.core.database.Driver;
import ws.nmathe.saber.core.google.CalendarConverter;
import ws.nmathe.saber.core.schedule.EntryManager;
import ws.nmathe.saber.core.settings.BotSettingsManager;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.GuildSettingsManager;
import ws.nmathe.saber.utils.HttpUtilities;
import ws.nmathe.saber.utils.Logging;

/**
 * Load point for the bot application
 * Used to connect the various important elements together
 */
public class Main
{
    private static ShardManager shardManager;
    private static BotSettingsManager botSettingsManager     = new BotSettingsManager();
    private static EntryManager entryManager                 = new EntryManager();
    private static ScheduleManager scheduleManager           = new ScheduleManager();
    private static CommandHandler commandHandler             = new CommandHandler();
    private static CalendarConverter calendarConverter       = new CalendarConverter();
    private static GuildSettingsManager guildSettingsManager = new GuildSettingsManager();
    private static Driver mongoDriver                        = new Driver();

    /**
     * initialize the bot
     */
    public static void main(String[] args)
    {
        if(botSettingsManager.hasSettings())
        {
            Logging.info(Main.class, "A 'saber.toml' configuration file has been created. Add your " +
                    "bot token to the file and restart the bot.\n");
            System.exit(0);
        }

        mongoDriver.init();         // ready database
        calendarConverter.init();   // connect to calendar service

        // create the shard manager
        shardManager = new ShardManager(botSettingsManager.getShards(), botSettingsManager.getShardTotal());
    }

    /*
     * Getters for manager type objects
     */

    public static ShardManager getShardManager()
    {
        return shardManager;
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
}
