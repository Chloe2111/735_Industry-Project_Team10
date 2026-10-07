package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Story 24: a submission from the community form goes through intake and tiering
 * before anything is published. Uses the real exceptions queue and in-memory stores.
 */
class IntakeServiceTest {

    private static final String GROUP = "youth group a";
    private static final String TEXT = "The workshop was well organised. I enjoyed the group activities.";

    private InMemoryGroupTierStore tierStore;
    private InMemorySubmissionStore submissionStore;
    private ExceptionsQueueService queue;
    private RecordingPipeline pipeline;
    private IntakeService intake;

    @BeforeEach
    void setUp() {
        tierStore = new InMemoryGroupTierStore();
        submissionStore = new InMemorySubmissionStore();
        queue = new ExceptionsQueueService();
        pipeline = new RecordingPipeline();
        intake = new IntakeService(new GroupTierService(tierStore), submissionStore, pipeline, queue);
    }

    private void classify(String groupId, int tier) {
        intake.classifyGroup(new TierRequest(groupId, null, tier, "Mohika", "Set for the test"));
    }

    private SubmissionReceipt submit(String... groupIds) {
        return intake.submit(new SubmissionRequest(List.of(groupIds), TEXT));
    }

    private Submission stored(String id) {
        return submissionStore.findById(id).orElseThrow();
    }

    private List<ExceptionItem> reviewItems(String submissionId) {
        return queue.listBySource(IntakeService.SOURCE_TYPE, submissionId);
    }

    // --- 24.2 and 24.3: the form reaches the endpoint, and the submitter is told the tier ---

    @Test
    void cleanSubmissionIsPublishedAndTheReceiptShowsTheGroupsTier() {
        classify(GROUP, 2);
        pipeline.returnsClean();

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.PUBLISHED, receipt.status());
        assertEquals(2, receipt.tier());
        assertEquals("Personal or sensitive", receipt.tierLabel());
        assertEquals(List.of(GROUP), receipt.groupIds());
        assertNotNull(receipt.id());
        assertNotNull(receipt.submittedAt());
        assertEquals(1, pipeline.calls.size());
        assertEquals(new RecordingPipeline.Call(receipt.id(), 2, TEXT), pipeline.calls.get(0));

        List<PublishedSubmission> published = intake.listPublished(GROUP);
        assertEquals(1, published.size());
        assertEquals(TEXT, published.get(0).text());
        assertTrue(queue.list().isEmpty(), "a clean submission needs no review");
    }

    @Test
    void tierUsedAtIntakeIsCopiedOntoTheSubmissionAndSurvivesARetier() {
        classify(GROUP, 1);
        SubmissionReceipt first = submit(GROUP);

        classify(GROUP, 2);
        SubmissionReceipt second = submit(GROUP);

        assertEquals(1, stored(first.id()).getTierAtIntake());
        assertEquals(2, stored(second.id()).getTierAtIntake());
        assertEquals(1, intake.getReceipt(first.id()).orElseThrow().tier());
    }

    // --- Tiering rules ---

    @Test
    void unclassifiedGroupIsRefusedAndNothingIsStoredOrProcessed() {
        UnclassifiedGroupException refused =
                assertThrows(UnclassifiedGroupException.class, () -> submit("brand new group"));

        assertEquals(List.of("brand new group"), refused.getGroupIds());
        assertTrue(pipeline.calls.isEmpty(), "the pipeline must not run for an untiered group");
        assertTrue(queue.list().isEmpty());
        assertTrue(submissionStore.findByGroupId("brand new group").isEmpty());
    }

    @Test
    void submissionToSeveralGroupsIsHandledUnderTheMostRestrictiveTier() {
        classify("a", 1);
        classify("b", 2);
        classify("c", 3);

        assertEquals(2, submit("a", "b").tier());

        int callsBefore = pipeline.calls.size();
        SubmissionReceipt restricted = submit("a", "c");
        assertEquals(3, restricted.tier());
        assertEquals(SubmissionStatus.HELD, restricted.status());
        assertEquals(callsBefore, pipeline.calls.size(), "one Tier 3 group keeps the whole submission out of the pipeline");

        assertThrows(UnclassifiedGroupException.class, () -> submit("a", "nobody classified this"));
    }

    // --- Flagged submissions and human review ---

    @Test
    void flaggedSubmissionIsHiddenAndSentToTheQueueTaggedAsACommunitySubmission() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded");

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status());
        assertTrue(intake.listPublished(GROUP).isEmpty(), "a flagged submission must not be visible");

        List<ExceptionItem> items = reviewItems(receipt.id());
        assertEquals(1, items.size());
        assertEquals(FlagType.QUOTE_NOT_FOUND, items.get(0).getFlagType());
        assertEquals(ReviewStatus.PENDING, items.get(0).getStatus());
        assertEquals(IntakeService.SOURCE_TYPE, items.get(0).getSourceType());
        assertEquals(receipt.id(), items.get(0).getSourceId());
        assertFalse(items.get(0).getSourceContext().contains(TEXT), "the queue must not carry the raw submission text");
        assertEquals(List.of(items.get(0).getId()), stored(receipt.id()).getExceptionIds());
        assertEquals(List.of("quoteNotGrounded"), stored(receipt.id()).getFlags());
    }

    @Test
    void clearingTheFlagPublishesTheSubmission() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);

        queue.clear(reviewItems(receipt.id()).get(0).getId(), "Checked against the source");

        assertEquals(SubmissionStatus.PUBLISHED, intake.getReceipt(receipt.id()).orElseThrow().status());
        assertEquals(1, intake.listPublished(GROUP).size());
    }

    @Test
    void submissionWithTwoFlagsIsPublishedOnlyWhenBothAreCleared() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);
        List<ExceptionItem> items = reviewItems(receipt.id());
        assertEquals(2, items.size());

        queue.clear(items.get(0).getId(), "");
        assertEquals(SubmissionStatus.PENDING_REVIEW, stored(receipt.id()).getStatus());
        assertTrue(intake.listPublished(GROUP).isEmpty());

        queue.clear(items.get(1).getId(), "");
        assertEquals(SubmissionStatus.PUBLISHED, stored(receipt.id()).getStatus());
    }

    @Test
    void rejectingHidesTheSubmissionButKeepsItRecoverable() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);
        String itemId = reviewItems(receipt.id()).get(0).getId();

        queue.reject(itemId, "Quote is not in the source");

        Submission rejected = stored(receipt.id());
        assertEquals(SubmissionStatus.REJECTED, rejected.getStatus());
        assertEquals(TEXT, rejected.getText(), "a rejected submission is hidden, not deleted");
        assertTrue(intake.listPublished(GROUP).isEmpty());

        // A reviewer who changes their mind can clear the same item and bring it back.
        queue.clear(itemId, "Re-checked: quote is acceptable");
        assertEquals(SubmissionStatus.PUBLISHED, stored(receipt.id()).getStatus());
    }

    @Test
    void oneRejectedFlagKeepsItHiddenEvenIfAnotherIsCleared() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);
        List<ExceptionItem> items = reviewItems(receipt.id());

        queue.reject(items.get(0).getId(), "Fabricated quote");
        queue.clear(items.get(1).getId(), "");

        assertEquals(SubmissionStatus.REJECTED, stored(receipt.id()).getStatus());
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void reviewingAStory18CodingFindingNeverChangesASubmission() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);

        // Worst case: a coding finding in the shared queue happens to use the submission's id as its sourceRef.
        ExceptionItem codingA = queue.add(new ExceptionItem(null, FlagType.QUOTE_NOT_FOUND, "q", "c", 0.9, receipt.id()));
        ExceptionItem codingB = queue.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.3, receipt.id()));

        // Even another feature that tags its items with the very same id must not trigger a publish.
        ExceptionItem otherFeature = new ExceptionItem(null, FlagType.CONTESTED, "q", "c", 0.6, receipt.id());
        otherFeature.setSourceType("CODING_FINDING");
        otherFeature.setSourceId(receipt.id());
        queue.add(otherFeature);

        assertEquals(ReviewStatus.CLEARED, queue.clear(codingA.getId(), "").orElseThrow().getStatus());
        assertEquals(ReviewStatus.REJECTED, queue.reject(codingB.getId(), "Not supported").orElseThrow().getStatus());
        assertEquals(ReviewStatus.CLEARED, queue.clear(otherFeature.getId(), "").orElseThrow().getStatus());

        assertEquals(SubmissionStatus.PENDING_REVIEW, stored(receipt.id()).getStatus());
        assertTrue(intake.listPublished(GROUP).isEmpty());
        assertEquals(ReviewStatus.PENDING, reviewItems(receipt.id()).get(0).getStatus());
    }

    @Test
    void anotherFeaturesItemCannotPublishASubmissionThatHasNoReviewItemsOfItsOwn() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("UNSUPPORTED_TEST_FLAG"); // hidden, with no review item the queue understands
        SubmissionReceipt receipt = submit(GROUP);
        ExceptionItem otherFeature = new ExceptionItem(null, FlagType.CONTESTED, "q", "c", 0.6, receipt.id());
        otherFeature.setSourceType("CODING_FINDING");
        otherFeature.setSourceId(receipt.id());
        queue.add(otherFeature);

        queue.clear(otherFeature.getId(), "");

        assertEquals(SubmissionStatus.PENDING_REVIEW, stored(receipt.id()).getStatus());
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    // --- Re-tiering a group ---

    @Test
    void movingAGroupToTier3HaltsItsQueuedSubmissions() {
        classify(GROUP, 1);
        pipeline.returnsClean();
        SubmissionReceipt alreadyPublished = submit(GROUP);
        pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);

        ClassificationResult result = intake.classifyGroup(
                new TierRequest(GROUP, null, 3, "Chloee", "Cultural knowledge is now shared in this group"));

        assertEquals(List.of(queued.id()), result.haltedSubmissionIds());
        assertEquals(1, result.publishedToRecheck());
        assertEquals(SubmissionStatus.HELD, stored(queued.id()).getStatus());
        assertEquals(SubmissionStatus.PUBLISHED, stored(alreadyPublished.id()).getStatus());

        ExceptionItem item = reviewItems(queued.id()).get(0);
        assertTrue(item.getSourceContext().startsWith(IntakeService.HALTED_PREFIX), "the reviewer must see it was halted");

        // Even if a reviewer clears the old queue item, a halted submission is not published.
        queue.clear(item.getId(), "");
        assertEquals(SubmissionStatus.HELD, stored(queued.id()).getStatus());
        assertEquals(1, intake.listPublished(GROUP).size());
    }

    @Test
    void changingBetweenTier1And2HaltsNothing() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);

        ClassificationResult result = intake.classifyGroup(new TierRequest(GROUP, null, 2, "Chloee", "More personal content"));

        assertTrue(result.haltedSubmissionIds().isEmpty());
        assertEquals(0, result.publishedToRecheck());
        assertEquals(SubmissionStatus.PENDING_REVIEW, stored(queued.id()).getStatus());
    }

    // --- Nothing slips through when something goes wrong ---

    @Test
    void pipelineFailureMeansNothingIsStoredOrPublished() {
        classify(GROUP, 1);
        pipeline.failsWith(new IOException("coder unreachable"));

        assertThrows(PipelineUnavailableException.class, () -> submit(GROUP));

        assertTrue(submissionStore.findByGroupId(GROUP).isEmpty());
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void unexpectedPipelineResultIsHeldNotPublished() {
        classify(GROUP, 1);
        pipeline.returnsStage("something_new", "unexpected");

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertNull(stored(receipt.id()).getText());
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void flagTheQueueDoesNotKnowStaysHiddenAndSaysItNeedsManualReview() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("UNSUPPORTED_TEST_FLAG");

        SubmissionReceipt receipt = submit(GROUP);

        Submission saved = stored(receipt.id());
        assertEquals(SubmissionStatus.PENDING_REVIEW, saved.getStatus());
        assertTrue(saved.getExceptionIds().isEmpty());
        assertTrue(saved.getStatusReason().contains("manual review"));
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void outOfRangeConfidenceFromTheCoderStillReachesTheQueue() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("lowConfidence");
        pipeline.returnsConfidence(85.0);

        SubmissionReceipt receipt = submit(GROUP);

        List<ExceptionItem> items = reviewItems(receipt.id());
        assertEquals(1, items.size());
        assertNull(items.get(0).getConfidence());
    }

    @Test
    void reviewItemsAreRestoredAfterARestartEmptiesTheInMemoryQueue() {
        classify(GROUP, 1);
        pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);

        // Restart: the stores keep their data, the queue and services are new.
        ExceptionsQueueService queueAfterRestart = new ExceptionsQueueService();
        IntakeService intakeAfterRestart =
                new IntakeService(new GroupTierService(tierStore), submissionStore, pipeline, queueAfterRestart);
        assertTrue(queueAfterRestart.list().isEmpty());

        intakeAfterRestart.listGroups(); // first intake call after the restart

        List<ExceptionItem> restored = queueAfterRestart.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, restored.size());
        assertEquals(FlagType.QUOTE_NOT_FOUND, restored.get(0).getFlagType());

        queueAfterRestart.clear(restored.get(0).getId(), "");
        assertEquals(SubmissionStatus.PUBLISHED, stored(receipt.id()).getStatus());
        assertEquals(0, intakeAfterRestart.restoreReviewQueue(), "restoring twice must not duplicate review items");
    }

    // --- Request validation ---

    @Test
    void incompleteSubmissionsAreRejectedBeforeAnythingRuns() {
        classify(GROUP, 1);

        assertThrows(IllegalArgumentException.class, () -> intake.submit(null));
        assertThrows(IllegalArgumentException.class, () -> intake.submit(new SubmissionRequest(null, TEXT)));
        assertThrows(IllegalArgumentException.class, () -> intake.submit(new SubmissionRequest(List.of(), TEXT)));
        assertThrows(IllegalArgumentException.class, () -> intake.submit(new SubmissionRequest(List.of("  "), TEXT)));
        assertThrows(IllegalArgumentException.class, () -> intake.submit(new SubmissionRequest(List.of(GROUP), "   ")));
        assertThrows(IllegalArgumentException.class, () -> intake.submit(new SubmissionRequest(List.of(GROUP), null)));
        assertThrows(IllegalArgumentException.class,
                () -> intake.submit(new SubmissionRequest(List.of(GROUP), "x".repeat(IntakeService.MAX_TEXT_LENGTH + 1))));

        assertTrue(pipeline.calls.isEmpty());
        assertTrue(submissionStore.findByGroupId(GROUP).isEmpty());
    }

    @Test
    void unknownSubmissionHasNoReceiptAndPublishedListNeedsAGroup() {
        assertTrue(intake.getReceipt("does-not-exist").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> intake.listPublished("  "));
        assertThrows(IllegalArgumentException.class, () -> intake.listPublished(null));
    }
}
