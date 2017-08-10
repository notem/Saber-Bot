package ws.nmathe.saber.core.database;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

/**
 */
public class DatabasePruner implements Runnable
{
    @Override
    public void run()
    {
        Logging.info(this.getClass(), "Running database pruner. . .");
        // purge guild setting entries for any guild not connected to the bot
        Main.getDBDriver().getGuildCollection().find().forEach((Consumer<? super Document>) document -> {
            String guildId = document.getString("_id");
            try
            {
                Guild guild = Main.getBotJda().getGuildById(guildId);
                if(guild == null)
                {
                    Main.getDBDriver().getGuildCollection().deleteOne(eq("_id", guildId));
                    Main.getDBDriver().getEventCollection().deleteMany(eq("guildId", guildId));
                    Main.getDBDriver().getScheduleCollection().deleteMany(eq("guildId", guildId));
                    Logging.info(this.getClass(), "Pruned guild with ID: " + guildId);
                }
            }
            catch(Exception e)
            {
                Logging.exception(this.getClass(), e);
            }
        });

        // purge schedule entries that the bot cannot connect to
        Main.getDBDriver().getScheduleCollection().find().forEach((Consumer<? super Document>) document -> {
            String chanId = document.getString("_id");
            try
            {
                MessageChannel channel = Main.getBotJda().getTextChannelById(chanId);
                if(channel == null)
                {
                    Main.getDBDriver().getEventCollection().deleteMany(eq("channeldId", chanId));
                    Main.getDBDriver().getScheduleCollection().deleteMany(eq("_id", chanId));
                    Logging.info(this.getClass(), "Pruned schedule with channel ID: " + chanId);
                }
            }
            catch(Exception e)
            {
                Logging.exception(this.getClass(), e);
            }
        });

        // purge events for which the bot cannot access the message
        Main.getDBDriver().getEventCollection().find().forEach((Consumer<? super Document>) document -> {
            String eventId = document.getString("_id");
            String messageId = document.getString("messageId");
            String channelId = document.getString("channelId");
            try
            {
                MessageChannel channel = Main.getBotJda().getTextChannelById(channelId);
                channel.getMessageById(messageId).queue(
                        message ->
                        {
                            if(message == null)
                            {
                                Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                                Logging.info(this.getClass(), "Pruned event with ID: " + eventId + " on channel with ID: " + channelId);
                            }
                        },
                        throwable ->
                        {
                            Logging.exception(this.getClass(), throwable);
                            Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                            Logging.info(this.getClass(), "Pruned event with ID: " + eventId + " on channel with ID: " + channelId);
                        });
            }
            catch(Exception e)
            {
                Logging.exception(this.getClass(), e);
            }
        });
    }
}
