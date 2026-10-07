package com.vcnity.backend;

import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/"
                + "vcnity_story24_dev?serverSelectionTimeoutMS=5000",
        "spring.data.mongodb.database=vcnity_story24_dev"
})
@EnabledIfSystemProperty(
        named = "mongodb.integration",
        matches = "true"
)
class MongoPersistenceIntegrationTest {

    private static final String COLLECTION =
            "story24_connection_checks";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void connectsAndPersistsSyntheticRecord() {
        assertEquals(
                "vcnity_story24_dev",
                mongoTemplate.getDb().getName()
        );

        Document ping = mongoTemplate.getDb()
                .runCommand(new Document("ping", 1));

        assertEquals(
                1.0,
                ((Number) ping.get("ok")).doubleValue()
        );

        String id = "synthetic-check-" + UUID.randomUUID();

        Document record = new Document("_id", id)
                .append("sourceRef", "synthetic_transcript_001")
                .append("text", "Synthetic database connection check.");

        Query recordQuery = Query.query(
                Criteria.where("_id").is(id)
        );

        try {
            mongoTemplate.insert(record, COLLECTION);

            Document saved = mongoTemplate.findById(
                    id,
                    Document.class,
                    COLLECTION
            );

            assertNotNull(saved);
            assertEquals(id, saved.getString("_id"));
            assertEquals(
                    "synthetic_transcript_001",
                    saved.getString("sourceRef")
            );
            assertEquals(
                    "Synthetic database connection check.",
                    saved.getString("text")
            );
        } finally {
            // Remove only this test's uniquely identified record.
            mongoTemplate.remove(recordQuery, COLLECTION);
        }

        assertFalse(
                mongoTemplate.exists(recordQuery, COLLECTION)
        );
    }
}