package ws.nmathe.saber.core.database;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;
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
        MongoClient mongoClient = new MongoClient(new MongoClientURI(Main.getBotSettingsManager().getMongoURI()));
        db = mongoClient.getDatabase("saberDB");

        // schedule a thread to prune disconnected guild, schedules, and events from the database
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("DatabasePruner-%d").build());
        executor.scheduleAtFixedRate(new Pruner(), 12*60*60, 12*60*60, TimeUnit.SECONDS);
    }

    public MongoCollection<Document> getScheduleCollection()
    {
        return db.getCollection("schedules")
                .withWriteConcern(WriteConcern.MAJORITY);
    }

    public MongoCollection<Document> getEventCollection()
    {
        return db.getCollection("events")
                .withWriteConcern(WriteConcern.MAJORITY);
    }

    public MongoCollection<Document> getGuildCollection()
    {
        return db.getCollection("guilds")
                .withWriteConcern(WriteConcern.MAJORITY);
    }
}
