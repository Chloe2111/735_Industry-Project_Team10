package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub Task 24.4: a Tier 3 submission is clearly flagged to the submitter
 * and is never silently passed into coding.
 */
class Tier3SubmissionTest {

    private static final String GROUP = "elders circle";
    private static final String TEXT = "This story should not be shared outside the community.";

    private InMemorySubmissionStore submissionStore;
    private ExceptionsQueueService queue;
    private RecordingPipeline pipeline;
    private IntakeService intake;

    @BeforeEach
    void setUp() {
        submissionStore = new InMemorySubmissionStore();
        queue = new ExceptionsQueueService();
        pipeline = new RecordingPipeline();
        intake = new IntakeService(new GroupTierService(new InMemoryGroupTierStore()), submissionStore, pipeline, queue);
        intake.classifyGroup(new TierRequest(GROUP, "Elders Circle", 3, "Mohika", "Cultural knowledge is shared here"));
    }

    private SubmissionReceipt submitTier3() {
        return intake.submit(new SubmissionRequest(List.of(GROUP), TEXT));
    }

    @Test
    void tier3SubmissionNeverReachesThePipeline() {
        submitTier3();

        assertTrue(pipeline.calls.isEmpty(), "Tier 3 must stop before the tier gate, de-identification or coding run");
    }

    @Test
    void tier3SubmissionIsFlaggedBackToTheSubmitter() {
        SubmissionReceipt receipt = submitTier3();

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertEquals(3, receipt.tier());
        assertEquals("Culturally restricted", receipt.tierLabel());
        assertTrue(receipt.message().contains("Tier 3"), "the submitter must be told why it was held");
        assertTrue(receipt.message().contains("not processed"));
    }

    @Test
    void tier3SubmissionIsRecordedButItsTextIsNotStored() {
        SubmissionReceipt receipt = submitTier3();

        Submission held = submissionStore.findById(receipt.id()).orElseThrow();
        assertEquals(SubmissionStatus.HELD, held.getStatus());
        assertEquals(3, held.getTierAtIntake());
        assertNull(held.getText());
        assertNull(held.getTheme());
        assertNull(held.getQuote());
        assertEquals(List.of(receipt.id()),
                submissionStore.findByStatus(SubmissionStatus.HELD).stream().map(Submission::getId).toList(),
                "held submissions can be listed, so none goes missing silently");
    }

    @Test
    void tier3SubmissionIsNeverPublishedOrQueuedForCodingReview() {
        SubmissionReceipt receipt = submitTier3();

        assertTrue(intake.listPublished(GROUP).isEmpty());
        assertTrue(queue.list().isEmpty(), "Tier 3 material must not enter the AI exceptions queue either");
        assertEquals(SubmissionStatus.HELD, intake.getReceipt(receipt.id()).orElseThrow().status());
    }

    @Test
    void ifThePipelineIsEverReachedItsOwnGateResultIsHeldToo() {
        // Second line of defence: a pipeline answer of "rejected_at_gate" is never treated as publishable.
        intake.classifyGroup(new TierRequest("group two", null, 2, "Mohika", "Sensitive"));
        pipeline.returnsStage("rejected_at_gate", "Tier 3 material is community-controlled");

        SubmissionReceipt receipt = intake.submit(new SubmissionRequest(List.of("group two"), TEXT));

        assertEquals(SubmissionStatus.HELD, receipt.status());
        assertNull(submissionStore.findById(receipt.id()).orElseThrow().getText());
        assertTrue(intake.listPublished("group two").isEmpty());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void theRealPipelineAlsoRefusesToCodeTier3() throws Exception {
        var repository = org.mockito.Mockito.mock(
                com.vcnity.backend.findings.FindingRepository.class);
        var coderCalls = new java.util.concurrent.atomic.AtomicInteger();

        PipelineService service = new PipelineService(sourceText -> {
            coderCalls.incrementAndGet();
            return List.of(new com.vcnity.backend.security.CodingDraft(
                    "Workshop feedback", sourceText, 0.95));
        });

        DefaultPipelineRunner realPipeline = new DefaultPipelineRunner(
                service, java.util.Set.of(), java.util.Set.of(), repository);

        PipelineService.PipelineOutcome outcome =
                realPipeline.run("T3-CHECK", 3, TEXT);

        assertEquals("rejected_at_gate", outcome.stageReached());
        assertNull(outcome.theme());
        assertNull(outcome.quote());
        assertEquals(0, coderCalls.get(), "Tier 3 must never reach the coder");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void tier1SubmissionStillGoesThroughTheRealPipeline() {
        var repository = org.mockito.Mockito.mock(
                com.vcnity.backend.findings.FindingRepository.class);
        org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any(
                com.vcnity.backend.security.CodedFinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var coderCalls = new java.util.concurrent.atomic.AtomicInteger();
        PipelineService service = new PipelineService(sourceText -> {
            coderCalls.incrementAndGet();
            return List.of(new com.vcnity.backend.security.CodingDraft(
                    "Workshop feedback", sourceText, 0.95));
        });

        DefaultPipelineRunner realPipeline = new DefaultPipelineRunner(
                service, java.util.Set.of(), java.util.Set.of(), repository);

        IntakeService realIntake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()),
                new InMemorySubmissionStore(),
                realPipeline,
                new ExceptionsQueueService());

        realIntake.classifyGroup(new TierRequest(
                "open group", null, 1, "Mohika", "General feedback"));

        SubmissionReceipt receipt = realIntake.submit(
                new SubmissionRequest(
                        List.of("open group"),
                        "The workshop was well organised. I enjoyed it."));

        assertEquals(1, receipt.tier());
        assertNotEquals(SubmissionStatus.HELD, receipt.status());
        assertEquals(1, coderCalls.get(), "Persistence must not rerun coding");

        var saved = org.mockito.ArgumentCaptor.forClass(
                com.vcnity.backend.security.CodedFinding.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        assertEquals(receipt.id(), saved.getValue().sourceRef());
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }
}
