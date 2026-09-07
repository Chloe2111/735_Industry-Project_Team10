package com.example;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertOneResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * IMPORTANT: keep the "package ...;" line that VS Code already put at the
 * very top of your generated App.java file — don't delete it. Paste
 * everything below (starting from the imports above) underneath it,
 * replacing whatever was there before.
 *
 * Before running:
 * 1. Make sure the mongodb-driver-sync dependency is in your pom.xml.
 * 2. Paste your real Atlas connection string into CONNECTION_STRING below,
 *    replacing <username> and <password> with the ones you created.
 *
 * Note: hardcoding a password directly in code like this is fine for
 * learning, but never do this in a real project you share or push to
 * GitHub — use an environment variable instead once you're comfortable.
 */
public class App {

    private static final String CONNECTION_STRING = Secrets.CONNECTION_STRING;

    public static void main(String[] args) {

        if (CONNECTION_STRING.contains("<username>") || CONNECTION_STRING.contains("<password>")) {
            System.err.println("Please replace <username> and <password> in CONNECTION_STRING first.");
            return;
        }

        // try-with-resources automatically closes the connection when done
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {

            // Atlas will create the database and collection automatically
            // the first time you write data to them
            MongoDatabase database = mongoClient.getDatabase("myFirstDatabase");
            MongoCollection<Document> collection = database.getCollection("people");

            System.out.println("Connected to MongoDB Atlas successfully!");

            // ---- CREATE ----
            Document newPerson = new Document("name", "Ada Lovelace")
                    .append("age", 28)
                    .append("occupation", "Mathematician");
            InsertOneResult result = collection.insertOne(newPerson);
            System.out.println("Inserted document with id: " + result.getInsertedId());

            // ---- READ ----
            Document found = collection.find(eq("name", "Ada Lovelace")).first();
            System.out.println("Found document: " + (found != null ? found.toJson() : "none"));

            // ---- UPDATE ----
            Bson filter = eq("name", "Ada Lovelace");
            Bson update = set("age", 29);
            collection.updateOne(filter, update);
            System.out.println("Updated Ada's age to 29");

            // ---- DELETE ----
            // Uncomment the line below to remove the document again
            // collection.deleteOne(eq("name", "Ada Lovelace"));

        } catch (Exception e) {
            System.err.println("Something went wrong connecting to MongoDB:");
            e.printStackTrace();
        }
    }
}
