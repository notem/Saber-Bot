package ws.nmathe.saber.core.settings;

import org.bson.Document;
import ws.nmathe.saber.Main;
import java.util.ArrayList;
import java.util.Arrays;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 */
public class GuildSettingsManager
{
    public GuildSettings getGuildSettings(String guildId)
    {
        Document guildDoc = Main.getDBDriver().getGuildCollection().find(eq("_id", guildId)).first();

        if(guildDoc == null) // create a new guild document and add to db
        {
            // unrestricted commands are commands that may be used outside of the command channel
            ArrayList<String> unrestrictedCommands = new ArrayList<>(Arrays.asList("list", "listm", "help")); // defaults

            // initialize with defaults
            guildDoc = new Document()
                    .append("_id", guildId)
                    .append("prefix", Main.getBotSettingsManager().getCommandPrefix())
                    .append("unrestricted_commands", unrestrictedCommands);

            Main.getDBDriver().getGuildCollection().insertOne(guildDoc);
        }

        return new GuildSettings(guildDoc);
    }

    /**
     */
    public static class GuildSettings
    {
        String guildId;
        String commandPrefix;
        ArrayList<String> unrestrictedCommands;
        String commandChannelId;

        GuildSettings(Document guildDocument)
        {
            guildId = guildDocument.getString("_id");
            commandPrefix = guildDocument.getString("prefix");
            commandChannelId = guildDocument.get("command_channel") != null ? guildDocument.getString("command_channel") : null;
            unrestrictedCommands = (ArrayList<String>) guildDocument.get("unrestricted_commands");
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

        // **** setters ****

        public void setPrefix(String prefix)
        {
            Main.getDBDriver().getGuildCollection().updateOne(eq("_id", guildId), set("prefix", prefix));
            this.commandPrefix = prefix;
        }

        public void setCommandChannelId(String channelId)
        {
            Main.getDBDriver().getGuildCollection().updateOne(eq("_id", guildId), set("command_channel", channelId));
            this.commandChannelId = channelId;
        }

        public void setUnrestrictedCommands(ArrayList<String> unrestrictedCommands)
        {
            Main.getDBDriver().getGuildCollection().updateOne(eq("_id", guildId),
                    set("unrestricted_commands", unrestrictedCommands));
            this.unrestrictedCommands = unrestrictedCommands;
        }

    }
}
