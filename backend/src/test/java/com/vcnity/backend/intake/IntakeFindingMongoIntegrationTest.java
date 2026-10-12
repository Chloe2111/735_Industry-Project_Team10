package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.findings.FindingRepository;
import com.vcnity.backend.findings.MongoFindingRepository;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/vcnity_story24_intake_test?serverSelectionTimeoutMS=5000",
        "spring.data.mongodb.database=vcnity_story24_intake_test"
})
@Import(MongoFindingRepository.class)
@EnabledIfSystemProperty(named = "mongodb.integration", matches = "true")
class IntakeFindingMongoIntegrationTest {

    @Autowired
    private FindingRepository repository;

    @Autowired
    private MongoTemplate mongo;

    private final String marker = "synthetic-intake-test-" + UUID.randomUUID();

    @AfterEach
    void removeOnlyThisTestsFindings() {
        mongo.remove(
                Query.query(Criteria.where("theme").is(marker)),
                "findings"
        );
    }

    @Test
    void intakeSavesAllFindingsToMongoAndQueuesTheFlaggedSubmissionOnce() {
        AtomicInteger coderCalls = new AtomicInteger();

        PipelineService pipeline = new PipelineService(text -> {
            coderCalls.incrementAndGet();
            return List.of(
                    new CodingDraft(
                            marker, "The workshop was helpful.", 0.95),
                    new CodingDraft(
                            marker, "Everyone requested a bus route.", 0.95)
            );
        });

        DefaultPipelineRunner runner = new DefaultPipelineRunner(
                pipeline, Set.of(), Set.of(), repository);
        ExceptionsQueueService queue = new ExceptionsQueueService();
        InMemorySubmissionStore submissions = new InMemorySubmissionStore();

        IntakeService intake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()),
                submissions, runner, queue);

        String group = "synthetic workshop group";
        intake.classifyGroup(new TierRequest(
                group, null, 1, "Test reviewer", "Synthetic integration test"));

        SubmissionReceipt receipt = intake.submit(new SubmissionRequest(
                List.of(group),
                "The workshop was helpful. We need more seating."
        ));

        assertEquals(1, coderCalls.get());
        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status());
        assertTrue(intake.listPublished(group).isEmpty());

        FindingRepository.FindingPage page =
                repository.findBySourceRef(receipt.id(), 0, 20);

        assertEquals(2, page.findings().size());
        assertFalse(page.hasNext());
        assertEquals(2L, mongo.getCollection("findings").countDocuments(
                new org.bson.Document("sourceRef", receipt.id())
        ));

        assertEquals(1L, page.findings().stream()
                .filter(finding -> finding.flags().isEmpty()).count());
        assertEquals(1L, page.findings().stream()
                .filter(finding -> finding.flags().contains("quoteNotGrounded"))
                .count());

        for (var finding : page.findings()) {
            assertEquals(marker, finding.theme());
            assertEquals(receipt.id(), finding.sourceRef());
            assertNull(finding.speakerCode());
            assertEquals(
                    finding,
                    repository.findByItemId(finding.itemId()).orElseThrow()
            );
        }

        assertNotEquals(
                page.findings().get(0).itemId(),
                page.findings().get(1).itemId()
        );

        assertEquals(1, queue.list().size());
        var reviewItems =
                queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, reviewItems.size());
        assertEquals(
                List.of(reviewItems.get(0).getId()),
                submissions.findById(receipt.id()).orElseThrow().getExceptionIds()
        );
    }
}
