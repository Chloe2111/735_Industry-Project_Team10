package com.vcnity.backend.findings;

import com.vcnity.backend.security.CodedFinding;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/"
                + "vcnity_story24_dev?serverSelectionTimeoutMS=5000",
        "spring.data.mongodb.database=vcnity_story24_dev"
})
@Import(MongoFindingRepository.class)
@EnabledIfSystemProperty(
        named = "mongodb.integration",
        matches = "true"
)
class MongoFindingRepositoryIntegrationTest {

    @Autowired
    private MongoFindingRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<String> testIds = new ArrayList<>();

    @BeforeEach
    void prepare() {
        assertEquals(
                "vcnity_story24_dev",
                mongoTemplate.getDb().getName()
        );
        repository.ensureIndexes();
    }

    @AfterEach
    void removeOnlyThisTestsRecords() {
        if (!testIds.isEmpty()) {
            mongoTemplate.execute(
                    MongoFindingRepository.COLLECTION,
                    collection -> collection.deleteMany(
                            new Document(
                                    "_id",
                                    new Document("$in", testIds)
                            )
                    )
            );
        }
    }

    @Test
    void savesAndReadsAllEightFields() {
        CodedFinding finding = newFinding(
                uniqueSource(),
                "PERSON_001",
                List.of()
        );

        assertEquals(finding, repository.save(finding));
        assertEquals(
                finding,
                repository.findByItemId(finding.itemId()).orElseThrow()
        );

        Document stored = readRaw(finding.itemId());

        assertNotNull(stored);
        assertEquals(finding.itemId(), stored.getString("_id"));
        assertEquals(finding.itemId(), stored.getString("itemId"));
        assertEquals(finding.sourceRef(), stored.getString("sourceRef"));
        assertEquals("PERSON_001", stored.getString("speakerCode"));
        assertEquals(finding.theme(), stored.getString("theme"));
        assertEquals(finding.quote(), stored.getString("quote"));
        assertEquals(finding.confidence(), stored.getDouble("confidence"));
        assertEquals(finding.tier(), stored.getInteger("tier"));
        assertEquals(
                finding.flags(),
                stored.getList("flags", String.class)
        );
    }

    @Test
    void preservesExplicitNullSpeakerAndFlags() {
        CodedFinding finding = newFinding(
                uniqueSource(),
                null,
                List.of("lowConfidence", "quoteNotGrounded")
        );

        repository.save(finding);

        Document stored = readRaw(finding.itemId());

        assertNotNull(stored);
        assertTrue(stored.containsKey("speakerCode"));
        assertNull(stored.get("speakerCode"));
        assertEquals(
                finding,
                repository.findByItemId(finding.itemId()).orElseThrow()
        );
    }

    @Test
    void identicalRetryDoesNotCreateDuplicate() {
        CodedFinding finding = newFinding(
                uniqueSource(),
                null,
                List.of()
        );

        repository.save(finding);
        assertEquals(finding, repository.save(finding));

        Long count = mongoTemplate.execute(
                MongoFindingRepository.COLLECTION,
                collection -> collection.countDocuments(
                        new Document("_id", finding.itemId())
                )
        );

        assertEquals(Long.valueOf(1), count);
    }

    @Test
    void conflictingRetryDoesNotOverwriteOriginal() {
        CodedFinding original = newFinding(
                uniqueSource(),
                "PERSON_001",
                List.of()
        );

        repository.save(original);

        CodedFinding changed = new CodedFinding(
                original.itemId(),
                original.sourceRef(),
                original.speakerCode(),
                "A different theme",
                original.quote(),
                original.confidence(),
                original.tier(),
                original.flags()
        );

        assertThrows(
                MongoFindingRepository.FindingConflictException.class,
                () -> repository.save(changed)
        );

        assertEquals(
                original,
                repository.findByItemId(original.itemId()).orElseThrow()
        );
    }

    @Test
    void filtersBySourceAndPaginatesInItemIdOrder() {
        String source = uniqueSource();

        List<CodedFinding> expected = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            CodedFinding finding = newFinding(
                    source,
                    "PERSON_001",
                    List.of()
            );
            repository.save(finding);
            expected.add(finding);
        }

        // This finding must not appear in the requested source's results.
        repository.save(newFinding(
                uniqueSource(),
                "PERSON_002",
                List.of()
        ));

        expected.sort(
                java.util.Comparator.comparing(CodedFinding::itemId)
        );

        FindingRepository.FindingPage first =
                repository.findBySourceRef(source, 0, 2);

        assertEquals(expected.subList(0, 2), first.findings());
        assertEquals(0, first.page());
        assertEquals(2, first.size());
        assertTrue(first.hasNext());

        FindingRepository.FindingPage second =
                repository.findBySourceRef(source, 1, 2);

        assertEquals(expected.subList(2, 3), second.findings());
        assertFalse(second.hasNext());

        FindingRepository.FindingPage beyond =
                repository.findBySourceRef(source, 2, 2);

        assertTrue(beyond.findings().isEmpty());
        assertFalse(beyond.hasNext());
    }

    @Test
    void missingFindingAndSourceReturnEmptyResults() {
        assertTrue(
                repository.findByItemId(
                        "synthetic-missing-" + UUID.randomUUID()
                ).isEmpty()
        );

        FindingRepository.FindingPage page =
                repository.findBySourceRef(uniqueSource(), 0, 20);

        assertTrue(page.findings().isEmpty());
        assertFalse(page.hasNext());
    }

    @Test
    void rejectsInvalidRepositoryInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByItemId(" ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findBySourceRef(null, 0, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findBySourceRef(" ", 0, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findBySourceRef("synthetic", -1, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findBySourceRef("synthetic", 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findBySourceRef("synthetic", 0, 101)
        );
    }

    private CodedFinding newFinding(
            String sourceRef,
            String speakerCode,
            List<String> flags
    ) {
        String id = "synthetic-finding-" + UUID.randomUUID();
        testIds.add(id);

        return new CodedFinding(
                id,
                sourceRef,
                speakerCode,
                "Access to community activities",
                "We need affordable transport to attend community workshops.",
                0.92,
                2,
                flags
        );
    }

    private String uniqueSource() {
        return "synthetic-source-" + UUID.randomUUID();
    }

    private Document readRaw(String itemId) {
        return mongoTemplate.execute(
                MongoFindingRepository.COLLECTION,
                collection -> collection.find(
                        new Document("_id", itemId)
                ).first()
        );
    }
}