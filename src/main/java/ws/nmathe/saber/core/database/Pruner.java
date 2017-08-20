package ws.nmathe.saber.core.database;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.where;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

/**
 */
public class Pruner implements Runnable
{
    @Override
    public void run()
    {
        // if the bot is not connected to the discord websocket, do not prune
        if(JDA.Status.valueOf("CONNECTED") != Main.getBotJda().getStatus()) return;

        Logging.info(this.getClass(), "Running database pruner. . .");

        // purge guild setting entries for any guild not connected to the bot
        Bson query = new Document();
        if(Main.isSharding())
        {
            query = and(query, where(Main.getShardingEvalString("_id")));
        }

        Main.getDBDriver().getGuildCollection().find(query)
                .projection(fields(include("_id")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        String guildId = document.getString("_id");
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
        query = new Document();
        if(Main.isSharding())
        {
            query = and(query, where(Main.getShardingEvalString("guildId")));
        }

        Main.getDBDriver().getScheduleCollection().find(query)
                .projection(fields(include("_id")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        String chanId = document.getString("_id");
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
        query = new Document();
        if(Main.isSharding())
        {
            query = and(query, where(Main.getShardingEvalString("guildId")));
        }

        Main.getDBDriver().getEventCollection().find(query)
                .projection(fields(include("_id", "messageId", "channelId")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        Integer eventId = document.getInteger("_id");
                        String messageId = document.getString("messageId");
                        if(messageId == null)
                        {
                            Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                            Logging.info(this.getClass(), "Pruned event with ID: " + eventId);
                            return;
                        }

                        String channelId = document.getString("channelId");
                        MessageChannel channel = Main.getBotJda().getTextChannelById(channelId);
                        if(channel==null)
                        {
                            Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                            Logging.info(this.getClass(), "Pruned event with ID: " + eventId);
                            return;
                        }

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
