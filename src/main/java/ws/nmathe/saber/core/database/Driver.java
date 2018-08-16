package ws.nmathe.saber.core.database;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import ws.nmathe.saber.Main;

import java.util.concurrent.*;

public class Driver
{
    private MongoDatabase db;

    public void init()
    {
        // for a connection to the Mongo database
        // connection properties should be configured via the URI used in the bot toml file
        MongoClient mongoClient = new MongoClient(new MongoClientURI(Main.getBotSettingsManager().getMongoURI()));
        db = mongoClient.getDatabase("saberDB");

        // schedule a thread to prune disconnected guild, schedules, and events from the database
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(new Pruner(), 12, 12, TimeUnit.HOURS);
    }

    public MongoCollection<Document> getScheduleCollection()
    {
        return db.getCollection("schedules");
    }

    public MongoCollection<Document> getEventCollection()
    {
        return db.getCollection("events");
    }

    public MongoCollection<Document> getGuildCollection()
    {
        return db.getCollection("guilds");
    }
}
