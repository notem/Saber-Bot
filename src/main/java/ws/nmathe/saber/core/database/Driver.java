package ws.nmathe.saber.core.database;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;

public class Driver {
    private MongoDatabase db;

    public void init()
    {
        MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
        db = mongoClient.getDatabase("saberDB");
    }

    public MongoCollection<Document> getScheduleCollection()
    {
        return db.getCollection("schedules");
    }

    public MongoCollection<Document> getEventCollection()
    {
        return db.getCollection("events");
    }
}
