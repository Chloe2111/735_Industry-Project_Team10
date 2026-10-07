package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Story 24: a submission from the community form goes through intake and tiering
 * before anything is published. Uses the real exceptions queue and in-memory stores.
 */
class IntakeServiceTest {

    private static final String GROUP = "youth group a";
    private static final String TEXT = "The workshop was well organised. I enjoyed the group activities.";

    private IntakeFixture app;

    @BeforeEach
    void setUp() {
        app = new IntakeFixture();
    }

    private SubmissionReceipt submit(String... groupIds) {
        return app.submit(TEXT, groupIds);
    }

    private ExceptionItem communityItem(String submissionId) {
        ExceptionItem item = new ExceptionItem(null, FlagType.CONTESTED, "q", "c", 0.6, submissionId);
        item.setSourceType(IntakeService.SOURCE_TYPE);
        item.setSourceId(submissionId);
        return item;
    }

    // --- 24.2 and 24.3: the form reaches the endpoint, and the submitter is told the tier ---

    @Test
    void cleanSubmissionIsPublishedAndTheReceiptShowsTheGroupsTier() {
        app.classify(GROUP, 2);
        app.pipeline.returnsClean();

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.PUBLISHED, receipt.status());
        assertEquals(2, receipt.tier());
        assertEquals(List.of(GROUP), receipt.groupIds());
        assertNotNull(receipt.id());
        assertNotNull(receipt.submittedAt());
        assertEquals(1, app.pipeline.calls.size());
        assertEquals(new RecordingPipeline.Call(receipt.id(), 2, TEXT), app.pipeline.calls.get(0));

        List<PublishedSubmission> published = app.intake.listPublished(GROUP);
        assertEquals(1, published.size());
        assertEquals(TEXT, published.get(0).text());
        assertTrue(app.queue.list().isEmpty(), "a clean submission needs no review");
    }

    @Test
    void tierUsedAtIntakeIsCopiedOntoTheSubmissionAndSurvivesARetier() {
        app.classify(GROUP, 1);
        SubmissionReceipt first = submit(GROUP);

        app.classify(GROUP, 2);
        SubmissionReceipt second = submit(GROUP);

        assertEquals(1, app.stored(first.id()).getTierAtIntake());
        assertEquals(2, app.stored(second.id()).getTierAtIntake());
        assertEquals(1, app.intake.getReceipt(first.id()).orElseThrow().tier());
    }

    // --- Tiering rules ---

    @Test
    void unclassifiedGroupIsRefusedAndNothingIsStoredOrProcessed() {
        UnclassifiedGroupException refused =
                assertThrows(UnclassifiedGroupException.class, () -> submit("brand new group"));

        assertEquals(422, refused.getStatus());
        assertEquals(List.of("brand new group"), refused.getGroupIds());
        assertTrue(app.pipeline.calls.isEmpty(), "the pipeline must not run for an untiered group");
        assertTrue(app.queue.list().isEmpty());
        assertTrue(app.submissionStore.findByStatus(SubmissionStatus.PUBLISHED).isEmpty());
        assertTrue(app.submissionStore.findByStatus(SubmissionStatus.HELD).isEmpty());
    }

    @Test
    void submissionToSeveralGroupsIsHandledUnderTheMostRestrictiveTier() {
        app.classify("a", 1);
        app.classify("b", 2);
        app.classify("c", 3);

        assertEquals(2, submit("a", "b").tier());

        int callsBefore = app.pipeline.calls.size();
        SubmissionReceipt restricted = submit("a", "c");
        assertEquals(3, restricted.tier());
        assertEquals(SubmissionStatus.HELD, restricted.status());
        assertEquals(callsBefore, app.pipeline.calls.size(), "one Tier 3 group keeps the whole submission out of the pipeline");

        assertThrows(UnclassifiedGroupException.class, () -> submit("a", "nobody classified this"));
    }

    // --- Flagged submissions and human review ---

    @Test
    void flaggedSubmissionIsHiddenAndSentToTheQueueTaggedAsACommunitySubmission() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded");

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status());
        assertTrue(app.intake.listPublished(GROUP).isEmpty(), "a flagged submission must not be visible");

        List<ExceptionItem> items = app.reviewItems(receipt.id());
        assertEquals(1, items.size());
        assertEquals(FlagType.QUOTE_NOT_FOUND, items.get(0).getFlagType());
        assertEquals(ReviewStatus.PENDING, items.get(0).getStatus());
        assertEquals(IntakeService.SOURCE_TYPE, items.get(0).getSourceType());
        assertEquals(receipt.id(), items.get(0).getSourceId());
        assertNull(items.get(0).getBlockedReason());
        assertFalse(items.get(0).getSourceContext().contains(TEXT), "the queue must not carry the raw submission text");
        assertEquals(List.of("quoteNotGrounded"), app.stored(receipt.id()).getFlags());
    }

    @Test
    void clearingTheFlagPublishesTheSubmission() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);

        app.queue.clear(app.reviewItems(receipt.id()).get(0).getId(), "Checked against the source");

        assertEquals(SubmissionStatus.PUBLISHED, app.intake.getReceipt(receipt.id()).orElseThrow().status());
        assertEquals(1, app.intake.listPublished(GROUP).size());
    }

    @Test
    void submissionWithTwoFlagsIsPublishedOnlyWhenBothAreCleared() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);
        List<ExceptionItem> items = app.reviewItems(receipt.id());
        assertEquals(2, items.size());

        app.queue.clear(items.get(0).getId(), "");
        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(receipt.id()).getStatus());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());

        app.queue.clear(items.get(1).getId(), "");
        assertEquals(SubmissionStatus.PUBLISHED, app.stored(receipt.id()).getStatus());
    }

    @Test
    void rejectingHidesTheSubmissionButKeepsItRecoverable() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);
        String itemId = app.reviewItems(receipt.id()).get(0).getId();

        app.queue.reject(itemId, "Quote is not in the source");

        Submission rejected = app.stored(receipt.id());
        assertEquals(SubmissionStatus.REJECTED, rejected.getStatus());
        assertEquals(TEXT, rejected.getText(), "a rejected submission is hidden, not deleted");
        assertTrue(app.intake.listPublished(GROUP).isEmpty());

        // A reviewer who changes their mind can clear the same item and bring it back.
        app.queue.clear(itemId, "Re-checked: quote is acceptable");
        assertEquals(SubmissionStatus.PUBLISHED, app.stored(receipt.id()).getStatus());
    }

    @Test
    void rejectedFlagDoesNotHideAFlagThatIsStillPending() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);
        List<ExceptionItem> items = app.reviewItems(receipt.id());

        app.queue.reject(items.get(0).getId(), "Fabricated quote");

        // One flag is rejected, but another still needs a person: the submission must still say so.
        Submission waiting = app.stored(receipt.id());
        assertEquals(SubmissionStatus.PENDING_REVIEW, waiting.getStatus());
        assertTrue(waiting.getStatusReason().startsWith("Waiting for a person to review"));
        assertEquals(1, app.queue.listPending().size(), "the other flag is still in the reviewer's queue");
        assertTrue(app.intake.listPublished(GROUP).isEmpty());

        // Only when every flag has been looked at is the outcome decided, and a reject keeps it hidden.
        app.queue.clear(items.get(1).getId(), "");
        assertEquals(SubmissionStatus.REJECTED, app.stored(receipt.id()).getStatus());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void reviewingAStory18CodingFindingNeverChangesASubmission() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);

        // Worst case: a coding finding in the shared queue happens to use the submission's id as its sourceRef.
        ExceptionItem codingA = app.queue.add(new ExceptionItem(null, FlagType.QUOTE_NOT_FOUND, "q", "c", 0.9, receipt.id()));
        ExceptionItem codingB = app.queue.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.3, receipt.id()));
        // Even another feature that tags its items with the very same id must not trigger a publish.
        ExceptionItem otherFeature = new ExceptionItem(null, FlagType.CONTESTED, "q", "c", 0.6, receipt.id());
        otherFeature.setSourceType("CODING_FINDING");
        otherFeature.setSourceId(receipt.id());
        app.queue.add(otherFeature);

        assertEquals(ReviewStatus.CLEARED, app.queue.clear(codingA.getId(), "").orElseThrow().getStatus());
        assertEquals(ReviewStatus.REJECTED, app.queue.reject(codingB.getId(), "Not supported").orElseThrow().getStatus());
        assertEquals(ReviewStatus.CLEARED, app.queue.clear(otherFeature.getId(), "").orElseThrow().getStatus());

        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(receipt.id()).getStatus());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
        assertEquals(ReviewStatus.PENDING, app.reviewItems(receipt.id()).get(0).getStatus());
    }

    @Test
    void anotherFeaturesItemCannotPublishASubmissionThatHasNoReviewItemsOfItsOwn() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("REPRESENTATION_GAP"); // hidden, with no review item the queue understands
        SubmissionReceipt receipt = submit(GROUP);
        ExceptionItem otherFeature = new ExceptionItem(null, FlagType.CONTESTED, "q", "c", 0.6, receipt.id());
        otherFeature.setSourceType("CODING_FINDING");
        otherFeature.setSourceId(receipt.id());
        app.queue.add(otherFeature);

        app.queue.clear(otherFeature.getId(), "");

        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(receipt.id()).getStatus());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
    }

    // --- Re-tiering a group, and "Clear" must never report a success that did not happen ---

    @Test
    void movingAGroupToTier3HaltsItsQueuedSubmissions() {
        app.classify(GROUP, 1);
        app.pipeline.returnsClean();
        SubmissionReceipt alreadyPublished = submit(GROUP);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);

        ClassificationResult result = app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");

        assertEquals(List.of(queued.id()), result.haltedSubmissionIds());
        assertEquals(1, result.publishedToRecheck());
        assertEquals(SubmissionStatus.HELD, app.stored(queued.id()).getStatus());
        assertEquals(SubmissionStatus.PUBLISHED, app.stored(alreadyPublished.id()).getStatus());
        assertEquals(IntakeService.HALTED_REASON, app.reviewItems(queued.id()).get(0).getBlockedReason(),
                "the reviewer must see that the item was halted");
        assertNull(app.reviewItems(queued.id()).get(0).getSourceQuote(),
                "the queue must stop showing text of a group that is now Tier 3");
    }

    @Test
    void groupMovedToTier3WhileTheSubmissionIsBeingCheckedIsHeldLikeAnyTier3Submission() {
        for (String outcome : List.of("clean", "flagged")) {
            IntakeFixture racing = new IntakeFixture();
            racing.classify(GROUP, 1);
            if (outcome.equals("clean")) racing.pipeline.returnsClean(); else racing.pipeline.returnsFlagged("lowConfidence");
            racing.pipeline.whileRunning(() -> racing.classify(GROUP, 3, "Chloee", "Now restricted"));

            SubmissionReceipt receipt = racing.submit(TEXT, GROUP);

            assertEquals(SubmissionStatus.HELD, receipt.status(), outcome);
            assertEquals(3, receipt.tier(), outcome);
            assertEquals(IntakeService.TIER3_HELD_MESSAGE, receipt.message());
            Submission held = racing.stored(receipt.id());
            assertEquals(TEXT, held.getText());
            assertNull(held.getTheme(), "the coding result is thrown away");
            assertTrue(racing.queue.list().isEmpty(), "no review item for a Tier 3 submission");
            assertTrue(racing.submissionStore.findByStatus(SubmissionStatus.PUBLISHED).isEmpty());
            assertTrue(racing.submissionStore.findByStatus(SubmissionStatus.PENDING_REVIEW).isEmpty());
        }
    }

    @Test
    void savingTier3AgainHaltsWhatAnEarlierFailedHaltLeftBehind() {
        boolean[] failNextHalt = {true};
        SubmissionStore failsOnce = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
                if (failNextHalt[0]) {
                    failNextHalt[0] = false;
                    throw new IllegalStateException("database dropped the connection");
                }
                return super.haltUnpublishedForGroup(groupId, reason, at);
            }
        };
        IntakeFixture flaky = new IntakeFixture(failsOnce);
        flaky.classify(GROUP, 1);
        flaky.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = flaky.submit(TEXT, GROUP);
        assertThrows(IllegalStateException.class, () -> flaky.classify(GROUP, 3, "Chloee", "Now restricted"));
        assertEquals(SubmissionStatus.PENDING_REVIEW, flaky.stored(queued.id()).getStatus(), "the halt failed");

        ClassificationResult retry = flaky.classify(GROUP, 3, "Chloee", "Now restricted (saved again)");

        assertEquals(List.of(queued.id()), retry.haltedSubmissionIds());
        assertEquals(SubmissionStatus.HELD, flaky.stored(queued.id()).getStatus());
    }

    @Test
    void aSubmissionTakenInAsTier3IsNeverListedOrPublishedWhateverItsStatusSays() {
        // Tier 3 records hold the text as submitted. Even if one were wrongly marked published or
        // waiting for review (a bug, or a manual database edit), it must not get out.
        app.classify(GROUP, 1);
        Submission wronglyPublished = tier3Record(SubmissionStatus.PUBLISHED);
        Submission wronglyWaiting = tier3Record(SubmissionStatus.PENDING_REVIEW);
        ExceptionItem item = app.queue.addExternal(communityItem(wronglyWaiting.getId()));

        assertTrue(app.intake.listPublished(GROUP).isEmpty());
        assertThrows(ReviewBlockedException.class, () -> app.queue.clear(item.getId(), ""));
        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(wronglyWaiting.getId()).getStatus());
    }

    private Submission tier3Record(SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setId(java.util.UUID.randomUUID().toString());
        submission.setGroupIds(List.of(GROUP));
        submission.setTierAtIntake(3);
        submission.setText("Restricted text as submitted.");
        submission.setStatus(status);
        submission.setSubmittedAt(Instant.now());
        submission.setUpdatedAt(Instant.now());
        return app.submissionStore.save(submission);
    }

    // --- Review point: a rejected post must not end up published after its group becomes Tier 3 ---

    @Test
    void rejectedSubmissionIsHaltedWhenItsGroupBecomesTier3AndCanNoLongerBeCleared() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);
        String itemId = app.reviewItems(receipt.id()).get(0).getId();
        app.queue.reject(itemId, "Quote is not in the source");
        assertEquals(SubmissionStatus.REJECTED, app.stored(receipt.id()).getStatus());

        ClassificationResult result = app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");

        assertEquals(List.of(receipt.id()), result.haltedSubmissionIds());
        assertEquals(SubmissionStatus.HELD, app.stored(receipt.id()).getStatus());
        assertEquals(IntakeService.HALTED_REASON, app.queue.get(itemId).orElseThrow().getBlockedReason());

        // Before the fix this clear published the post.
        ReviewBlockedException blocked =
                assertThrows(ReviewBlockedException.class, () -> app.queue.clear(itemId, "Changed my mind"));

        assertTrue(blocked.getMessage().contains("Tier 3"));
        assertEquals(ReviewStatus.REJECTED, app.queue.get(itemId).orElseThrow().getStatus(), "the item keeps its status");
        assertEquals(SubmissionStatus.HELD, app.stored(receipt.id()).getStatus());
        assertTrue(app.submissionStore.findByStatus(SubmissionStatus.PUBLISHED).isEmpty());
    }

    @Test
    void submissionWithOneClearedAndOneRejectedFlagIsHaltedToo() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");
        SubmissionReceipt receipt = submit(GROUP);
        List<ExceptionItem> items = app.reviewItems(receipt.id());
        app.queue.clear(items.get(0).getId(), "");
        app.queue.reject(items.get(1).getId(), "Too uncertain");

        app.classify(GROUP, 3, "Chloee", "Now restricted");

        assertThrows(ReviewBlockedException.class, () -> app.queue.clear(items.get(1).getId(), ""));
        assertEquals(SubmissionStatus.HELD, app.stored(receipt.id()).getStatus());
    }

    @Test
    void clearCannotPublishIntoATier3GroupEvenIfTheSubmissionWasNeverHalted() {
        // Second line of defence: the halt is skipped here, as if it had failed or not happened yet.
        // The review still checks the group's tier as it is now and refuses to publish.
        SubmissionStore haltDoesNothing = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
                return List.of();
            }
        };
        IntakeFixture unhalted = new IntakeFixture(haltDoesNothing);
        unhalted.classify(GROUP, 1);
        unhalted.pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt rejected = unhalted.submit(TEXT, GROUP);
        SubmissionReceipt waiting = unhalted.submit(TEXT, GROUP);
        String rejectedItem = unhalted.reviewItems(rejected.id()).get(0).getId();
        String waitingItem = unhalted.reviewItems(waiting.id()).get(0).getId();
        unhalted.queue.reject(rejectedItem, "Quote is not in the source");

        unhalted.classify(GROUP, 3, "Chloee", "Now restricted");
        assertEquals(SubmissionStatus.REJECTED, unhalted.stored(rejected.id()).getStatus(), "not halted in this test");

        assertThrows(ReviewBlockedException.class, () -> unhalted.queue.clear(rejectedItem, ""));
        assertThrows(ReviewBlockedException.class, () -> unhalted.queue.clear(waitingItem, ""));

        assertEquals(SubmissionStatus.REJECTED, unhalted.stored(rejected.id()).getStatus());
        assertEquals(SubmissionStatus.PENDING_REVIEW, unhalted.stored(waiting.id()).getStatus());
        assertTrue(unhalted.submissionStore.findByStatus(SubmissionStatus.PUBLISHED).isEmpty());
    }

    @Test
    void postSharedWithTwoGroupsCannotBeClearedOnceEitherGroupIsTier3() {
        app.classify(GROUP, 1);
        app.classify("group b", 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt receipt = submit(GROUP, "group b");
        String itemId = app.reviewItems(receipt.id()).get(0).getId();

        app.classify("group b", 3, "Chloee", "Now restricted");

        assertThrows(ReviewBlockedException.class, () -> app.queue.clear(itemId, ""));
        assertEquals(SubmissionStatus.HELD, app.stored(receipt.id()).getStatus());
    }

    // --- Review point: posts published before a group became Tier 3 must not stay visible ---

    @Test
    void publishedPostsAreHiddenWhileTheirGroupIsTier3() {
        app.classify(GROUP, 1);
        app.pipeline.returnsClean();
        SubmissionReceipt published = submit(GROUP);
        assertEquals(1, app.intake.listPublished(GROUP).size());

        ClassificationResult result = app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");

        assertEquals(1, result.publishedToRecheck(), "the coordinator is told how many posts were hidden");
        assertTrue(app.intake.listPublished(GROUP).isEmpty(), "nothing of a Tier 3 group is shown");
        SubmissionReceipt latest = app.intake.getReceipt(published.id()).orElseThrow();
        assertEquals(SubmissionStatus.HELD, latest.status(), "the submitter is not told it is still published");
        assertEquals(IntakeService.HIDDEN_BY_TIER3_MESSAGE, latest.message());
    }

    @Test
    void hiddenPostsAreNotDeletedAndShowAgainIfAPersonMovesTheGroupBack() {
        app.classify(GROUP, 1);
        app.pipeline.returnsClean();
        SubmissionReceipt published = submit(GROUP);
        app.classify(GROUP, 3, "Chloee", "Set by mistake");
        assertEquals(TEXT, app.stored(published.id()).getText(), "hidden, not deleted");

        app.classify(GROUP, 1, "Chloee", "Corrected: this group is general feedback");

        assertEquals(List.of(published.id()),
                app.intake.listPublished(GROUP).stream().map(PublishedSubmission::id).toList());
        assertEquals(SubmissionStatus.PUBLISHED, app.intake.getReceipt(published.id()).orElseThrow().status());
    }

    @Test
    void postSharedWithTwoGroupsIsHiddenInBothWhenOneBecomesTier3() {
        app.classify(GROUP, 1);
        app.classify("group b", 1);
        app.pipeline.returnsClean();
        SubmissionReceipt shared = submit(GROUP, "group b");
        SubmissionReceipt onlyInGroupA = submit(GROUP);

        app.classify("group b", 3, "Chloee", "Now restricted");

        assertTrue(app.intake.listPublished("group b").isEmpty());
        assertEquals(List.of(onlyInGroupA.id()),
                app.intake.listPublished(GROUP).stream().map(PublishedSubmission::id).toList(),
                "the shared post is hidden in the other group too; that group's own posts stay");
        assertEquals(SubmissionStatus.HELD, app.intake.getReceipt(shared.id()).orElseThrow().status());
    }

    // --- Review point: published posts must not carry names and contact details ---

    @Test
    void namesAndContactDetailsAreRemovedBeforeAnythingIsStoredOrPublished() {
        String withDetails = "Thanks to Maria Lopez for running it. Call me on 0412 345 678 or email maria.lopez@example.com.";
        app.classify(GROUP, 2);
        app.pipeline.returnsClean();

        SubmissionReceipt receipt = app.submit(withDetails, GROUP);

        String publishedText = app.intake.listPublished(GROUP).get(0).text();
        String storedText = app.stored(receipt.id()).getText();
        assertEquals(publishedText, storedText);
        for (String detail : List.of("Maria", "Lopez", "0412", "345 678", "example.com")) {
            assertFalse(storedText.contains(detail), "must not be stored or published: " + detail);
        }
        assertTrue(storedText.contains("[REDACTED:"), "the removed parts are marked, not silently dropped");
        assertTrue(storedText.contains("for running it."), "the rest of the feedback is kept");
        assertEquals(withDetails, app.pipeline.calls.get(0).text(), "the pipeline still receives the submission and de-identifies it itself");
    }

    @Test
    void flaggedSubmissionIsStoredDeidentifiedTooSoAReviewerNeverSeesTheOriginal() {
        app.classify(GROUP, 2);
        app.pipeline.returnsFlagged("lowConfidence");

        SubmissionReceipt receipt = app.submit("Ask Maria Lopez, her number is 0412 345 678", GROUP);

        String storedText = app.stored(receipt.id()).getText();
        assertFalse(storedText.contains("Maria"));
        assertFalse(storedText.contains("0412"));
        app.queue.clear(app.reviewItems(receipt.id()).get(0).getId(), "");
        assertEquals(storedText, app.intake.listPublished(GROUP).get(0).text());
    }

    @Test
    void ifDeidentificationFailsNothingIsStoredOrPublished() {
        app.classify(GROUP, 1);
        app.pipeline.returnsClean();
        app.pipeline.deidentifyFailsWith(new IllegalStateException("term list missing"));

        PipelineUnavailableException failure = assertThrows(PipelineUnavailableException.class, () -> submit(GROUP));

        assertEquals(503, failure.getStatus());
        for (SubmissionStatus status : SubmissionStatus.values()) {
            assertTrue(app.submissionStore.findByStatus(status).isEmpty());
        }
    }

    @Test
    void clearingAHaltedItemFailsWithTheReasonAndChangesNothing() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);
        app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");
        ExceptionItem item = app.reviewItems(queued.id()).get(0);

        ReviewBlockedException blocked =
                assertThrows(ReviewBlockedException.class, () -> app.queue.clear(item.getId(), "Looks fine"));

        assertEquals(409, blocked.getStatus());
        assertTrue(blocked.getMessage().contains("Tier 3"));
        assertEquals(ReviewStatus.PENDING, app.queue.get(item.getId()).orElseThrow().getStatus(),
                "the item must not be shown as cleared");
        assertNull(app.queue.get(item.getId()).orElseThrow().getReviewedAt());
        assertEquals(SubmissionStatus.HELD, app.stored(queued.id()).getStatus());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void heldSubmissionBlocksAClearEvenIfTheItemItselfWasNotMarked() {
        // For example an item that reached the queue after the group was halted.
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);
        ExceptionItem lateItem = app.queue.addExternal(communityItem(queued.id()));
        app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");
        lateItem.setBlockedReason(null);

        assertThrows(ReviewBlockedException.class, () -> app.queue.clear(lateItem.getId(), ""));

        assertEquals(ReviewStatus.PENDING, app.queue.get(lateItem.getId()).orElseThrow().getStatus());
        assertEquals(SubmissionStatus.HELD, app.stored(queued.id()).getStatus());
    }

    @Test
    void haltedItemCanStillBeRejectedAndTheSubmissionStaysHeld() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);
        app.classify(GROUP, 3, "Chloee", "Cultural knowledge is now shared in this group");
        ExceptionItem item = app.reviewItems(queued.id()).get(0);

        assertEquals(ReviewStatus.REJECTED, app.queue.reject(item.getId(), "Group is now Tier 3").orElseThrow().getStatus());

        assertEquals(SubmissionStatus.HELD, app.stored(queued.id()).getStatus());
    }

    @Test
    void reviewCannotPublishASubmissionThatWasHaltedWhileItWasBeingCleared() {
        // The submission is halted in the instant between the review reading it and writing the new status.
        InMemorySubmissionStore real = new InMemorySubmissionStore();
        SubmissionStore haltsJustBeforeTheWrite = new DelegatingSubmissionStore(real) {
            @Override
            public boolean changeStatus(String id, SubmissionStatus from, SubmissionStatus to, String reason, Instant at) {
                real.haltUnpublishedForGroup(GROUP, IntakeService.HALTED_REASON, Instant.now());
                return super.changeStatus(id, from, to, reason, at);
            }
        };
        IntakeFixture racing = new IntakeFixture(haltsJustBeforeTheWrite);
        racing.classify(GROUP, 1);
        racing.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = racing.submit(TEXT, GROUP);
        ExceptionItem item = racing.reviewItems(queued.id()).get(0);

        assertThrows(ReviewBlockedException.class, () -> racing.queue.clear(item.getId(), ""));

        assertEquals(SubmissionStatus.HELD, racing.stored(queued.id()).getStatus(), "the halt must win");
        assertEquals(ReviewStatus.PENDING, racing.queue.get(item.getId()).orElseThrow().getStatus());
        assertTrue(racing.intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void changingBetweenTier1And2HaltsNothing() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);

        ClassificationResult result = app.classify(GROUP, 2, "Chloee", "More personal content");

        assertTrue(result.haltedSubmissionIds().isEmpty());
        assertEquals(0, result.publishedToRecheck());
        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(queued.id()).getStatus());
        assertNull(app.reviewItems(queued.id()).get(0).getBlockedReason());
    }

    @Test
    void tierChangeBasedOnAStaleViewIsRefusedAndHaltsNothing() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt queued = submit(GROUP);
        Long versionBothSee = app.currentVersion(GROUP);
        app.intake.classifyGroup(new TierRequest(GROUP, null, 2, "Chloee", "More personal content", versionBothSee));

        assertThrows(ConcurrentTierChangeException.class, () -> app.intake.classifyGroup(
                new TierRequest(GROUP, null, 3, "Mohika", "Restricted", versionBothSee)));

        assertEquals(2, app.tierStore.findById(GROUP).orElseThrow().getTier());
        assertEquals(SubmissionStatus.PENDING_REVIEW, app.stored(queued.id()).getStatus(),
                "a refused tier change must not halt anything");
    }

    // --- Items posted through POST /api/exceptions ---

    @Test
    void postedItemMayOnlyBeTaggedForASubmissionThatIsWaitingForReview() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        SubmissionReceipt waiting = submit(GROUP);
        app.pipeline.returnsClean();
        SubmissionReceipt published = submit(GROUP);

        assertEquals(waiting.id(), app.queue.addExternal(communityItem(waiting.id())).getSourceId());

        assertThrows(IllegalArgumentException.class, () -> app.queue.addExternal(communityItem("no-such-submission")));
        assertThrows(IllegalArgumentException.class, () -> app.queue.addExternal(communityItem(published.id())),
                "otherwise rejecting the posted item would hide a published submission");

        assertTrue(app.reviewItems(published.id()).isEmpty());
        assertEquals(SubmissionStatus.PUBLISHED, app.stored(published.id()).getStatus());
    }

    // --- Nothing slips through when something goes wrong ---

    @Test
    void pipelineFailureMeansNothingIsStoredOrPublished() {
        app.classify(GROUP, 1);
        app.pipeline.failsWith(new IOException("coder unreachable"));

        PipelineUnavailableException failure = assertThrows(PipelineUnavailableException.class, () -> submit(GROUP));

        assertEquals(503, failure.getStatus());
        for (SubmissionStatus status : SubmissionStatus.values()) {
            assertTrue(app.submissionStore.findByStatus(status).isEmpty());
        }
    }

    @Test
    void unexpectedPipelineResultIsHeldNotPublished() {
        app.classify(GROUP, 1);
        app.pipeline.returnsStage("something_new", "unexpected");

        SubmissionReceipt receipt = submit(GROUP);

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertNull(app.stored(receipt.id()).getText());
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void flagTheQueueDoesNotKnowStaysHiddenAndSaysItNeedsManualReview() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("REPRESENTATION_GAP");

        SubmissionReceipt receipt = submit(GROUP);

        Submission saved = app.stored(receipt.id());
        assertEquals(SubmissionStatus.PENDING_REVIEW, saved.getStatus());
        assertTrue(app.reviewItems(receipt.id()).isEmpty());
        assertTrue(saved.getStatusReason().contains("manual review"));
        assertTrue(app.intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void outOfRangeConfidenceFromTheCoderStillReachesTheQueue() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("lowConfidence");
        app.pipeline.returnsConfidence(85.0);

        SubmissionReceipt receipt = submit(GROUP);

        List<ExceptionItem> items = app.reviewItems(receipt.id());
        assertEquals(1, items.size());
        assertNull(items.get(0).getConfidence());
    }

    // --- Startup: the in-memory queue is rebuilt from the stored submissions ---

    @Test
    void reviewItemsAreRestoredAtStartupAfterARestartEmptiesTheQueue() {
        app.classify(GROUP, 1);
        app.pipeline.returnsFlagged("quoteNotGrounded");
        SubmissionReceipt receipt = submit(GROUP);

        // Restart: the stores keep their data, the queue and services are new.
        ExceptionsQueueService queueAfterRestart = new ExceptionsQueueService();
        IntakeService intakeAfterRestart = new IntakeService(
                new GroupTierService(app.tierStore), app.submissionStore, app.pipeline, queueAfterRestart);
        assertTrue(queueAfterRestart.list().isEmpty());

        intakeAfterRestart.restoreReviewQueueAtStartup(); // Spring calls this once (@PostConstruct)

        List<ExceptionItem> restored = queueAfterRestart.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, restored.size());
        assertEquals(FlagType.QUOTE_NOT_FOUND, restored.get(0).getFlagType());

        queueAfterRestart.clear(restored.get(0).getId(), "");
        assertEquals(SubmissionStatus.PUBLISHED, app.stored(receipt.id()).getStatus());
        assertEquals(0, intakeAfterRestart.restoreReviewQueue(), "restoring twice must not duplicate review items");
    }

    @Test
    void startupStillSucceedsWhenTheDatabaseCannotBeReached() {
        SubmissionStore unreachable = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public List<Submission> findByStatus(SubmissionStatus status) {
                throw new IllegalStateException("database unreachable");
            }
        };
        IntakeFixture starting = new IntakeFixture(unreachable);

        starting.intake.restoreReviewQueueAtStartup(); // must log and carry on, not stop the app from starting

        assertTrue(starting.queue.list().isEmpty());
    }

    // --- One query, not one per record ---

    @Test
    void lookingUpTheTierOfSeveralGroupsIsOneQuery() {
        int[] singleLookups = new int[1];
        int[] bulkLookups = new int[1];
        InMemoryGroupTierStore counting = new InMemoryGroupTierStore() {
            @Override
            public Optional<GroupTier> findById(String groupId) {
                singleLookups[0]++;
                return super.findById(groupId);
            }

            @Override
            public List<GroupTier> findByIds(java.util.Collection<String> groupIds) {
                bulkLookups[0]++;
                return super.findByIds(groupIds);
            }
        };
        GroupTierService tiers = new GroupTierService(counting);
        for (String group : List.of("a", "b", "c", "d", "e")) {
            tiers.setTier(group, null, 1, "Mohika", "General", null);
        }
        singleLookups[0] = 0;

        assertEquals(1, tiers.resolve(List.of("a", "b", "c", "d", "e")).tier());

        assertEquals(1, bulkLookups[0]);
        assertEquals(0, singleLookups[0]);
    }

    @Test
    void haltingManySubmissionsIsOneBulkUpdateNotOneSaveEach() {
        int[] saves = new int[1];
        int[] bulkHalts = new int[1];
        SubmissionStore counting = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public Submission save(Submission submission) {
                saves[0]++;
                return super.save(submission);
            }

            @Override
            public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
                bulkHalts[0]++;
                return super.haltUnpublishedForGroup(groupId, reason, at);
            }
        };
        IntakeFixture counted = new IntakeFixture(counting);
        counted.classify(GROUP, 1);
        counted.pipeline.returnsFlagged("lowConfidence");
        for (int i = 0; i < 6; i++) {
            counted.submit(TEXT, GROUP);
        }
        saves[0] = 0;

        ClassificationResult result = counted.classify(GROUP, 3, "Chloee", "Now restricted");

        assertEquals(6, result.haltedSubmissionIds().size());
        assertEquals(1, bulkHalts[0]);
        assertEquals(0, saves[0], "no per-submission save while halting");
    }

    @Test
    void listingPublishedPostsIsTwoQueriesHoweverManyPostsThereAre() {
        int[] tierQueries = new int[1];
        int[] submissionQueries = new int[1];
        InMemoryGroupTierStore countingTiers = new InMemoryGroupTierStore() {
            @Override
            public Optional<GroupTier> findById(String groupId) {
                tierQueries[0]++;
                return super.findById(groupId);
            }

            @Override
            public List<GroupTier> findByIds(java.util.Collection<String> groupIds) {
                tierQueries[0]++;
                return super.findByIds(groupIds);
            }
        };
        SubmissionStore countingSubmissions = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public List<Submission> findByGroupIdAndStatus(String groupId, SubmissionStatus status) {
                submissionQueries[0]++;
                return super.findByGroupIdAndStatus(groupId, status);
            }

            @Override
            public Optional<Submission> findById(String id) {
                submissionQueries[0]++;
                return super.findById(id);
            }
        };
        RecordingPipeline pipeline = new RecordingPipeline();
        IntakeService counted = new IntakeService(
                new GroupTierService(countingTiers), countingSubmissions, pipeline, new ExceptionsQueueService());
        for (String group : List.of(GROUP, "group b", "group c")) {
            counted.classifyGroup(new TierRequest(group, null, 1, "Mohika", "General", null));
        }
        for (int i = 0; i < 8; i++) {
            counted.submit(new SubmissionRequest(List.of(GROUP, i % 2 == 0 ? "group b" : "group c"), TEXT));
        }
        tierQueries[0] = 0;
        submissionQueries[0] = 0;

        assertEquals(8, counted.listPublished(GROUP).size());

        assertEquals(1, submissionQueries[0]);
        assertEquals(1, tierQueries[0]);
    }

    @Test
    void flaggedSubmissionIsSavedOnce() {
        int[] saves = new int[1];
        SubmissionStore counting = new DelegatingSubmissionStore(new InMemorySubmissionStore()) {
            @Override
            public Submission save(Submission submission) {
                saves[0]++;
                return super.save(submission);
            }
        };
        IntakeFixture counted = new IntakeFixture(counting);
        counted.classify(GROUP, 1);
        counted.pipeline.returnsFlagged("quoteNotGrounded, lowConfidence");

        counted.submit(TEXT, GROUP);

        assertEquals(1, saves[0]);
    }

    // --- Request validation ---

    @Test
    void incompleteSubmissionsAreRejectedBeforeAnythingRuns() {
        app.classify(GROUP, 1);

        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(null));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(new SubmissionRequest(null, TEXT)));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(new SubmissionRequest(List.of(), TEXT)));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(new SubmissionRequest(List.of("  "), TEXT)));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(new SubmissionRequest(List.of(GROUP), "   ")));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(new SubmissionRequest(List.of(GROUP), null)));
        assertThrows(IllegalArgumentException.class, () -> app.intake.submit(
                new SubmissionRequest(List.of(GROUP), "x".repeat(IntakeService.MAX_TEXT_LENGTH + 1))));

        assertTrue(app.pipeline.calls.isEmpty());
        assertTrue(app.submissionStore.findByStatus(SubmissionStatus.PUBLISHED).isEmpty());
    }

    @Test
    void unknownSubmissionHasNoReceiptAndPublishedListNeedsAGroup() {
        assertTrue(app.intake.getReceipt("does-not-exist").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> app.intake.listPublished("  "));
        assertThrows(IllegalArgumentException.class, () -> app.intake.listPublished(null));
    }
}
