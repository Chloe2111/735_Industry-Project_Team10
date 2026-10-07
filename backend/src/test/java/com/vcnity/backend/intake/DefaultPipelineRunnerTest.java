package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

class DefaultPipelineRunnerTest {

    private static final String GROUP = "workshop group";
    private static final String TEXT = "The workshop helped us connect.";

    private DefaultPipelineRunner runner(
            Function<String, List<PipelineService.PipelineOutcome>> outcomes)
            throws Exception {
        PipelineService pipeline = mock(PipelineService.class);
        when(pipeline.runPipeline(anyList(), anySet(), anySet()))
                .thenAnswer(invocation -> {
                    List<PipelineService.PipelineItem> items = invocation.getArgument(0);
                    var all = outcomes.apply(items.get(0).itemId());
                    return new PipelineService.PipelineRun(
                            all,
                            all.stream().filter(o -> "clean".equals(o.stageReached())).toList(),
                            all.stream().filter(o -> "exceptions_queue".equals(o.stageReached())).toList(),
                            all.stream().filter(o -> "rejected_at_gate".equals(o.stageReached())).toList()
                    );
                });
        return new DefaultPipelineRunner(pipeline, Set.of(), Set.of());
    }

    private PipelineService.PipelineOutcome outcome(
            String id, String stage, String reason) {
        return new PipelineService.PipelineOutcome(
                id, stage, reason, "Community connection", TEXT, 0.9, 0
        );
    }

    private IntakeService intake(
            DefaultPipelineRunner runner, ExceptionsQueueService queue) {
        IntakeService intake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()),
                new InMemorySubmissionStore(), runner, queue
        );
        intake.classifyGroup(new TierRequest(
                GROUP, null, 1, "Test reviewer", "Synthetic test classification"
        ));
        return intake;
    }

    @Test
    void cleanFirstAndCoverageFlagSecondCreatesReviewAndDoesNotPublish()
            throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome(id, "exceptions_queue", "REPRESENTATION_GAP")
        ));
        var queue = new ExceptionsQueueService();
        var intake = intake(runner, queue);

        var receipt = intake.submit(new SubmissionRequest(List.of(GROUP), TEXT));

        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status());
        assertTrue(intake.listPublished(GROUP).isEmpty());
        var items = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, items.size());
        assertEquals(FlagType.REPRESENTATION_GAP, items.get(0).getFlagType());
        assertEquals(ReviewStatus.PENDING, items.get(0).getStatus());
    }

    @Test
    void clearingGroundingFlagDoesNotPublishWhileCoverageFlagIsPending()
            throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome(id, "exceptions_queue", "lowConfidence"),
                outcome(id, "exceptions_queue", "REPRESENTATION_GAP")
        ));
        var queue = new ExceptionsQueueService();
        var intake = intake(runner, queue);
        var receipt = intake.submit(new SubmissionRequest(List.of(GROUP), TEXT));
        var items = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(2, items.size());

        var lowConfidence = items.stream()
                .filter(i -> i.getFlagType() == FlagType.LOW_CONFIDENCE)
                .findFirst().orElseThrow();
        queue.clear(lowConfidence.getId(), "Confidence reviewed");

        assertEquals(SubmissionStatus.PENDING_REVIEW,
                intake.getReceipt(receipt.id()).orElseThrow().status());
        assertTrue(intake.listPublished(GROUP).isEmpty());
        assertTrue(items.stream().anyMatch(i ->
                i.getFlagType() == FlagType.REPRESENTATION_GAP
                        && i.getStatus() == ReviewStatus.PENDING));
    }

    @Test
    void flagBeforeCleanStillRequiresReviewAndDuplicateReasonsAreCombined()
            throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "exceptions_queue", "lowConfidence"),
                outcome(id, "clean", null),
                outcome(id, "exceptions_queue", "lowConfidence, REPRESENTATION_GAP")
        ));

        var result = runner.run("S1", 1, TEXT);

        assertEquals("exceptions_queue", result.stageReached());
        assertEquals("lowConfidence, REPRESENTATION_GAP", result.reason());
    }

    @Test
    void gateRejectionWinsOverCleanAndReview() throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome(id, "exceptions_queue", "lowConfidence"),
                outcome(id, "rejected_at_gate", "Restricted source")
        ));
        assertEquals("rejected_at_gate", runner.run("S1", 1, TEXT).stageReached());
    }

    @Test
    void cleanOnlyRemainsCleanAndOtherItemsAreNotMixedIn() throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome("OTHER", "exceptions_queue", "REPRESENTATION_GAP")
        ));
        assertEquals("clean", runner.run("S1", 1, TEXT).stageReached());
    }

    @Test
    void missingOrUnexpectedOutcomesFailClosed() throws Exception {
        var missing = runner(id -> List.of());
        assertThrows(IllegalStateException.class, () -> missing.run("S1", 1, TEXT));

        var unexpected = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome(id, "unexpected_stage", null)
        ));
        assertThrows(IllegalStateException.class, () -> unexpected.run("S1", 1, TEXT));
    }

    @Test
    void flaggedOutcomeWithoutAReasonFailsClosed() throws Exception {
        var runner = runner(id -> List.of(
                outcome(id, "clean", null),
                outcome(id, "exceptions_queue", null)
        ));
        assertThrows(IllegalStateException.class, () -> runner.run("S1", 1, TEXT));
    }
}
