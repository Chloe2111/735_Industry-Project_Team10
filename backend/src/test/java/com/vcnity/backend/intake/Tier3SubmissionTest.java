package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sub Task 24.4: a Tier 3 submission is clearly flagged to the submitter
 * and is never silently passed into coding.
 */
class Tier3SubmissionTest {

    private static final String GROUP = "elders circle";
    private static final String TEXT = "This story should not be shared outside the community.";

    private IntakeFixture app;

    @BeforeEach
    void setUp() {
        app = new IntakeFixture();
        app.classify(GROUP, 3, "Mohika", "Cultural knowledge is shared here");
    }

    private SubmissionReceipt submitTier3() {
        return app.submit(TEXT, GROUP);
    }

    @Test
    void tier3SubmissionNeverReachesThePipeline() {
        submitTier3();

        assertTrue(app.pipeline.calls.isEmpty(), "Tier 3 must stop before the tier gate, de-identification or coding run");
        assertTrue(app.pipeline.deidentifyCalls.isEmpty(), "Tier 3 text must not be sent to any automated step");
    }

    @Test
    void tier3SubmissionIsFlaggedBackToTheSubmitter() {
        SubmissionReceipt receipt = submitTier3();

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertEquals(3, receipt.tier());
        assertTrue(receipt.message().contains("Tier 3"), "the submitter must be told why it was held");
        assertTrue(receipt.message().contains("not processed"));
        assertTrue(receipt.message().contains("not published"));
        assertTrue(receipt.message().contains("kept for a person to review"));
    }

    @Test
    void tier3TextIsKeptForAPersonToReviewAndNothingAutomatedIsRecorded() {
        // Mohika's decision: the text is kept so a person can review it.
        SubmissionReceipt receipt = submitTier3();

        Submission held = app.stored(receipt.id());
        assertEquals(SubmissionStatus.HELD, held.getStatus());
        assertEquals(3, held.getTierAtIntake());
        assertEquals(TEXT, held.getText(), "kept exactly as submitted: no automated step may change it");
        assertNull(held.getTheme());
        assertNull(held.getQuote());
        assertEquals(List.of(receipt.id()),
                app.submissionStore.findByStatus(SubmissionStatus.HELD).stream().map(Submission::getId).toList(),
                "held submissions can be listed, so none goes missing silently");
    }

    @Test
    void tier3TextIsNotReturnedByAnyEndpoint() throws Exception {
        SubmissionReceipt receipt = submitTier3();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules();

        // Everything the API can return about this submission or its group, as JSON.
        String everythingReturned = mapper.writeValueAsString(receipt)
                + mapper.writeValueAsString(app.intake.getReceipt(receipt.id()).orElseThrow())
                + mapper.writeValueAsString(app.intake.listPublished(GROUP))
                + mapper.writeValueAsString(app.intake.listGroups())
                + mapper.writeValueAsString(app.queue.list());

        assertFalse(everythingReturned.contains("should not be shared"), "Tier 3 text must stay out of every response");
    }

    @Test
    void tier3SubmissionIsNeverPublishedOrQueuedForCodingReview() {
        SubmissionReceipt receipt = submitTier3();

        assertTrue(app.intake.listPublished(GROUP).isEmpty());
        assertTrue(app.queue.list().isEmpty(), "Tier 3 material must not enter the AI exceptions queue either");
        assertEquals(SubmissionStatus.HELD, app.intake.getReceipt(receipt.id()).orElseThrow().status());
    }

    @Test
    void nobodyCanPostAReviewItemForATier3SubmissionToGetItCleared() {
        SubmissionReceipt receipt = submitTier3();
        var forged = new com.vcnity.backend.exceptions.model.ExceptionItem(
                null, com.vcnity.backend.exceptions.model.FlagType.LOW_CONFIDENCE, "q", "c", 0.5, receipt.id());
        forged.setSourceType(IntakeService.SOURCE_TYPE);
        forged.setSourceId(receipt.id());

        assertThrows(IllegalArgumentException.class, () -> app.queue.addExternal(forged));

        assertTrue(app.queue.list().isEmpty());
        assertEquals(SubmissionStatus.HELD, app.stored(receipt.id()).getStatus());
    }

    @Test
    void ifThePipelineIsEverReachedItsOwnGateResultIsHeldToo() {
        // Second line of defence: a pipeline answer of "rejected_at_gate" is never treated as publishable.
        app.classify("group two", 2);
        app.pipeline.returnsStage("rejected_at_gate", "Tier 3 material is community-controlled");

        SubmissionReceipt receipt = app.submit(TEXT, "group two");

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertNull(app.stored(receipt.id()).getText());
        assertTrue(app.intake.listPublished("group two").isEmpty());
        assertTrue(app.queue.list().isEmpty());
    }

    @Test
    void theRealPipelineAlsoRefusesToCodeTier3() throws Exception {
        DefaultPipelineRunner realPipeline = new DefaultPipelineRunner();
        assumeTrue(realPipeline.isMockMode(), "skipped when a real API key is set, so tests never call the live model");

        PipelineService.PipelineOutcome outcome = realPipeline.run("T3-CHECK", 3, TEXT);

        assertEquals("rejected_at_gate", outcome.stageReached());
        assertNull(outcome.theme(), "no coding result may exist for Tier 3");
        assertNull(outcome.quote());
    }

    @Test
    void tier1SubmissionStillGoesThroughTheRealPipeline() {
        DefaultPipelineRunner realPipeline = new DefaultPipelineRunner();
        assumeTrue(realPipeline.isMockMode(), "skipped when a real API key is set, so tests never call the live model");
        IntakeService realIntake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()), new InMemorySubmissionStore(), realPipeline,
                new ExceptionsQueueService());
        realIntake.classifyGroup(new TierRequest("open group", null, 1, "Mohika", "General feedback", null));

        SubmissionReceipt receipt = realIntake.submit(
                new SubmissionRequest(List.of("open group"), "The workshop was well organised. I enjoyed it."));

        assertEquals(1, receipt.tier());
        assertNotEquals(SubmissionStatus.HELD, receipt.status(), "Tier 1 must be processed, not held");
    }
}
