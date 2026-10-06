package com.vcnity.backend.findings;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.BatchCodingWorkflow;
import com.vcnity.backend.security.BatchCodingJob;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.CodingSource;
import com.vcnity.backend.security.TranscriptCoder;
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
import java.util.concurrent.atomic.AtomicInteger;

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
class PersistedBatchCodingWorkflowIntegrationTest {

    private static final String TEXT =
            "We need affordable transport to attend community workshops.";

    @Autowired
    private MongoFindingRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private ExceptionsQueueService queue;
    private final List<String> testSources = new ArrayList<>();

    @BeforeEach
    void prepare() {
        assertEquals(
                "vcnity_story24_dev",
                mongoTemplate.getDb().getName()
        );

        repository.ensureIndexes();
        queue = new ExceptionsQueueService();
    }

    @AfterEach
    void cleanUpSyntheticFindings() {
        if (!testSources.isEmpty()) {
            mongoTemplate.execute(
                    MongoFindingRepository.COLLECTION,
                    collection -> collection.deleteMany(
                            new Document(
                                    "sourceRef",
                                    new Document("$in", testSources)
                            )
                    )
            );
        }
    }

    @Test
    void groundedFindingIsSavedAndWaitsForVerification()
            throws InterruptedException {

        CodingSource source = newSource();

        TranscriptCoder coder = text -> List.of(
                new CodingDraft(
                        "Access to community activities",
                        text,
                        0.95
                )
        );

        PersistedBatchCodingWorkflow.Result result =
                workflow(coder).run(List.of(source));

        assertEquals(1, result.workflowResult().sources().size());

        BatchCodingWorkflow.RoutedSource routed =
                result.workflowResult().sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_VERIFICATION,
                routed.routingStatus()
        );
        assertEquals(1, routed.codingOutcome().findings().size());

        CodedFinding finding = routed.codingOutcome().findings().get(0);

        assertTrue(finding.flags().isEmpty());
        assertEquals(source.sourceRef(), finding.sourceRef());
        assertEquals("PERSON_001", finding.speakerCode());

        assertEquals(
                finding,
                repository.findByItemId(finding.itemId()).orElseThrow()
        );

        assertEquals(1, result.persistenceReceipts().size());
        assertEquals(
                PersistedBatchCodingWorkflow.PersistenceStatus.SAVED,
                result.persistenceReceipts().get(0).status()
        );
        assertTrue(result.allProducedFindingsPersisted());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void flaggedFindingIsSavedAndRetryDoesNotRecodeOrRequeue()
            throws InterruptedException {

        CodingSource source = newSource();
        AtomicInteger coderCalls = new AtomicInteger();

        TranscriptCoder coder = text -> {
            coderCalls.incrementAndGet();

            return List.of(new CodingDraft(
                    "Access to community activities",
                    "The workshop provided free transport.",
                    0.95
            ));
        };

        PersistedBatchCodingWorkflow workflow = workflow(coder);

        PersistedBatchCodingWorkflow.Result result =
                workflow.run(List.of(source));

        BatchCodingWorkflow.RoutedSource routed =
                result.workflowResult().sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_EXCEPTION_REVIEW,
                routed.routingStatus()
        );

        CodedFinding finding = routed.codingOutcome().findings().get(0);

        assertTrue(finding.flags().contains("quoteNotGrounded"));
        assertFalse(routed.queueReceipts().isEmpty());
        assertTrue(routed.queueReceipts().stream().allMatch(
                receipt -> receipt.receipt().complete()
        ));
        assertFalse(queue.listPending().isEmpty());

        assertEquals(
                finding,
                repository.findByItemId(finding.itemId()).orElseThrow()
        );
        assertTrue(result.allProducedFindingsPersisted());

        int queueSize = queue.list().size();

        PersistedBatchCodingWorkflow.Result retry =
                workflow.persist(result.workflowResult());

        assertEquals(1, coderCalls.get());
        assertEquals(queueSize, queue.list().size());
        assertEquals(result.persistenceReceipts(), retry.persistenceReceipts());

        FindingRepository.FindingPage stored =
                repository.findBySourceRef(source.sourceRef(), 0, 20);

        assertEquals(List.of(finding), stored.findings());
        assertFalse(stored.hasNext());
    }

    @Test
    void noFindingsDoesNotCreatePlaceholderRecords()
            throws InterruptedException {

        CodingSource source = newSource();

        PersistedBatchCodingWorkflow.Result result =
                workflow(text -> List.of()).run(List.of(source));

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.NO_FINDINGS,
                result.workflowResult().sources().get(0).routingStatus()
        );
        assertTrue(result.persistenceReceipts().isEmpty());
        assertTrue(
                repository.findBySourceRef(
                        source.sourceRef(), 0, 20
                ).findings().isEmpty()
        );
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void coderFailureRemainsExplicitAndCreatesNoFinding()
            throws InterruptedException {

        CodingSource source = newSource();

        TranscriptCoder failingCoder = text -> {
            throw new IllegalStateException(
                    "Synthetic internal error details"
            );
        };

        PersistedBatchCodingWorkflow.Result result =
                workflow(failingCoder).run(List.of(source));

        BatchCodingWorkflow.RoutedSource routed =
                result.workflowResult().sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.CODING_FAILED,
                routed.routingStatus()
        );
        assertEquals(
                BatchCodingJob.Status.FAILED,
                routed.codingOutcome().status()
        );
        assertEquals(
                "CODER_FAILED",
                routed.codingOutcome().errorCode()
        );
        assertTrue(result.persistenceReceipts().isEmpty());
        assertTrue(
                repository.findBySourceRef(
                        source.sourceRef(), 0, 20
                ).findings().isEmpty()
        );
        assertTrue(queue.list().isEmpty());
    }

    private PersistedBatchCodingWorkflow workflow(
            TranscriptCoder coder
    ) {
        return new PersistedBatchCodingWorkflow(
                new BatchCodingWorkflow(coder, queue),
                repository
        );
    }

    private CodingSource newSource() {
        String sourceRef = "synthetic-workflow-" + UUID.randomUUID();
        testSources.add(sourceRef);

        return new CodingSource(
                sourceRef,
                TEXT,
                2,
                "PERSON_001"
        );
    }
}