package ws.nmathe.saber.core.settings;

import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.general.*;

import java.util.ArrayList;
import java.util.Arrays;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * manager for guild setting options
 */
public class GuildSettingsManager
{
    /**
     * retrieves the guild settings object for a guild
     * @param guildId ID of guild
     * @return GuildSettings object (never null)
     */
    public GuildSettings getGuildSettings(String guildId)
    {
        Document guildDoc = Main.getDBDriver().getGuildCollection().find(eq("_id", guildId)).first();

        if(guildDoc == null) // create a new guild document and add to db
        {
            // unrestricted commands are commands that may be used outside of the command channel
            ArrayList<String> unrestrictedCommands = new ArrayList<>(Arrays.asList(
                    new ListCommand().name(),
                    new HelpCommand().name(),
                    new SchedulesCommand().name(),
                    new EventsCommand().name(),
                    new DiagnoseCommand().name())); // defaults

            // initialize with defaults
            guildDoc = new Document()
                    .append("_id", guildId)
                    .append("prefix", Main.getBotSettingsManager().getCommandPrefix())
                    .append("unrestricted_commands", unrestrictedCommands)
                    .append("late_threshold", 15);

            Main.getDBDriver().getGuildCollection().insertOne(guildDoc);
        }

        return new GuildSettings(guildDoc);
    }

    /**
     * object for getting and setting guild options
     */
    @SuppressWarnings("unchecked")
    public static class GuildSettings
    {
        String guildId;
        String commandPrefix;
        ArrayList<String> unrestrictedCommands;
        String commandChannelId;
        Integer lateThreshold;

        GuildSettings(Document guildDocument)
        {
            guildId = guildDocument.getString("_id");
            commandPrefix = guildDocument.getString("prefix");
            commandChannelId = guildDocument.get("command_channel") != null ?
                    guildDocument.getString("command_channel") : null;
            unrestrictedCommands = (ArrayList<String>) guildDocument.get("unrestricted_commands");
            lateThreshold = guildDocument.get("late_threshold") != null ?
                    guildDocument.getInteger("late_threshold") : 15;
        }

        // **** getters ****

        public String getPrefix()
        {
            return this.commandPrefix;
        }

        public String getCommandChannelId()
        {
            return this.commandChannelId;
        }

        public ArrayList<String> getUnrestrictedCommands()
        {
            return unrestrictedCommands;
        }

        public ArrayList<String> getRestrictedCommands()
        {
            ArrayList<String> commands = new ArrayList<>(Main.getCommandHandler().getCommandNames());
            commands.removeAll(unrestrictedCommands);
            return commands;
        }

        public Integer getLateThreshold()
        {
            return this.lateThreshold;
        }

        // **** setters ****

        public void setPrefix(String prefix)
        {
            Main.getDBDriver().getGuildCollection()
                    .updateOne(eq("_id", guildId), set("prefix", prefix));
            this.commandPrefix = prefix;
        }

        public void setCommandChannelId(String channelId)
        {
            Main.getDBDriver().getGuildCollection()
                    .updateOne(eq("_id", guildId), set("command_channel", channelId));
            this.commandChannelId = channelId;
        }

        public void setUnrestrictedCommands(ArrayList<String> unrestrictedCommands)
        {
            Main.getDBDriver().getGuildCollection().updateOne(eq("_id", guildId),
                    set("unrestricted_commands", unrestrictedCommands));
            this.unrestrictedCommands = unrestrictedCommands;
        }

        public void setLateThreshold(Integer minutes)
        {
            Main.getDBDriver().getGuildCollection()
                    .updateOne(eq("_id", guildId), set("late_threshold", minutes));
            this.lateThreshold = minutes;
        }

    }
}
