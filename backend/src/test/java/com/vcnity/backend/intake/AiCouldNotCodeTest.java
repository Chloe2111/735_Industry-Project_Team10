package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.findings.FindingRepository;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.PipelineService;
import com.vcnity.backend.security.TranscriptCoder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When the AI cannot code a submission (the call fails, its answer is unusable, or it finds
 * nothing), intake keeps the de-identified text as "waiting for review" with the flag
 * AI_COULD_NOT_CODE, so a facilitator can code or reject it. It is never published or refused.
 *
 * Runs the real pipeline and runner with a scripted AI, so no API key or database is needed.
 */
class AiCouldNotCodeTest {

    private static final String GROUP = "riverside youth";
    private static final String TEXT = "Sarah Thompson said the evening paths feel unsafe after dark.";

    /** Keeps saved findings in memory, standing in for MongoDB. */
    private static final class MemoryFindings implements FindingRepository {
        final List<CodedFinding> saved = new ArrayList<>();

        @Override
        public CodedFinding save(CodedFinding finding) {
            saved.add(finding);
            return finding;
        }

        @Override
        public Optional<CodedFinding> findByItemId(String itemId) {
            return saved.stream().filter(f -> f.itemId().equals(itemId)).findFirst();
        }

        @Override
        public FindingPage findBySourceRef(String sourceRef, int page, int size) {
            return new FindingPage(List.of(), page, size, false);
        }
    }

    private final ExceptionsQueueService queue = new ExceptionsQueueService();
    private final InMemorySubmissionStore store = new InMemorySubmissionStore();
    private final MemoryFindings findings = new MemoryFindings();

    private IntakeService intakeWith(TranscriptCoder ai) {
        DefaultPipelineRunner runner = new DefaultPipelineRunner(
                new PipelineService(ai), Set.of(), Set.of(), findings);
        IntakeService intake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()), store, runner, queue);
        intake.classifyGroup(new TierRequest(GROUP, "Riverside Youth", 2, "Test coordinator", "Synthetic test", null));
        return intake;
    }

    private SubmissionReceipt submit(IntakeService intake) {
        return intake.submit(new SubmissionRequest(List.of(GROUP), TEXT));
    }

    private void assertSentForHumanCoding(IntakeService intake, SubmissionReceipt receipt) {
        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status(), "must wait for a person, not be refused");
        assertTrue(intake.listPublished(GROUP).isEmpty(), "must never be published automatically");

        Submission stored = store.findById(receipt.id()).orElseThrow();
        assertTrue(stored.getFlags().contains(DefaultPipelineRunner.AI_COULD_NOT_CODE));
        assertEquals(IntakeService.AI_COULD_NOT_CODE_STATUS, stored.getStatusReason());
        assertFalse(stored.getText().contains("Sarah Thompson"), "only de-identified text is stored");
        assertTrue(stored.getText().contains("[REDACTED:"));
        assertNull(stored.getTheme());
        assertNull(stored.getQuote());
        assertNull(stored.getConfidence());

        List<ExceptionItem> items = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, items.size());
        ExceptionItem item = items.get(0);
        assertEquals(FlagType.AI_COULD_NOT_CODE, item.getFlagType());
        assertEquals(ReviewStatus.PENDING, item.getStatus());
        assertEquals(stored.getText(), item.getSourceQuote(), "the reviewer sees the de-identified text");
        assertEquals(IntakeService.AI_COULD_NOT_CODE_CONTEXT, item.getSourceContext());
        assertNull(item.getConfidence());
        assertTrue(findings.saved.isEmpty(), "no AI finding exists to save");
    }

    @Test
    void aiCallThatFailsIsSentForHumanCodingNotRefused() {
        IntakeService intake = intakeWith(text -> {
            throw new IllegalStateException("upstream error");
        });
        assertSentForHumanCoding(intake, submit(intake));
    }

    @Test
    void unusableAiAnswerIsSentForHumanCoding() {
        // A blank theme is what the live call produces when it cannot read the reply.
        IntakeService intake = intakeWith(text -> List.of(new CodingDraft("", "", 0.0)));
        assertSentForHumanCoding(intake, submit(intake));
    }

    @Test
    void aiThatFindsNothingIsSentForHumanCoding() {
        IntakeService intake = intakeWith(text -> List.of());
        assertSentForHumanCoding(intake, submit(intake));
    }

    @Test
    void theAiOnlySeesDeIdentifiedText() {
        List<String> seen = new ArrayList<>();
        IntakeService intake = intakeWith(text -> {
            seen.add(text);
            return List.of();
        });
        submit(intake);
        assertEquals(1, seen.size());
        assertFalse(seen.get(0).contains("Sarah Thompson"));
    }

    @Test
    void aReviewerCanClearItAndThenPublishIt() {
        IntakeService intake = intakeWith(text -> List.of());
        SubmissionReceipt receipt = submit(intake);
        ExceptionItem item = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id()).get(0);

        queue.clear(item.getId(), "Read and coded by the facilitator");
        assertEquals(SubmissionStatus.CLEARED, intake.getReceipt(receipt.id()).orElseThrow().status(),
                "clearing never publishes on its own");

        assertEquals(SubmissionStatus.PUBLISHED, intake.publish(receipt.id()).orElseThrow().status());
        assertEquals(1, intake.listPublished(GROUP).size());
    }

    @Test
    void aReviewerCanRejectIt() {
        IntakeService intake = intakeWith(text -> List.of());
        SubmissionReceipt receipt = submit(intake);
        ExceptionItem item = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id()).get(0);

        queue.reject(item.getId(), "Not about this commission");
        assertEquals(SubmissionStatus.REJECTED, intake.getReceipt(receipt.id()).orElseThrow().status());
        assertTrue(intake.listPublished(GROUP).isEmpty());
    }

    @Test
    void theReviewItemComesBackAfterARestart() {
        IntakeService before = intakeWith(text -> List.of());
        SubmissionReceipt receipt = submit(before);

        // A restart empties the in-memory queue; the stored submission brings its item back.
        ExceptionsQueueService freshQueue = new ExceptionsQueueService();
        IntakeService after = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()), store,
                new DefaultPipelineRunner(new PipelineService(text -> List.of()), Set.of(), Set.of(), findings),
                freshQueue);
        assertEquals(1, after.restoreReviewQueue());

        List<ExceptionItem> items = freshQueue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, items.size());
        assertEquals(FlagType.AI_COULD_NOT_CODE, items.get(0).getFlagType());
        assertEquals(store.findById(receipt.id()).orElseThrow().getText(), items.get(0).getSourceQuote());
    }

    @Test
    void aGoodAiAnswerIsStillPublishedAsBefore() {
        IntakeService intake = intakeWith(text -> List.of(new CodingDraft("Safety", "the evening paths feel unsafe after dark", 0.9)));
        SubmissionReceipt receipt = submit(intake);

        assertEquals(SubmissionStatus.PUBLISHED, receipt.status());
        assertTrue(queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id()).isEmpty());
        assertEquals(1, findings.saved.size());
    }
}
