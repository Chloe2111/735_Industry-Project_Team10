package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import com.vcnity.backend.findings.FindingRepository;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PATCH /api/exceptions/{id}/code: a facilitator codes an idea the AI could not code.
 *
 * Runs the real intake, pipeline and queue with an AI that finds nothing, so every submission
 * lands in the queue as AI_COULD_NOT_CODE. No API key or database is needed.
 */
class FacilitatorCodingTest {

    private static final String GROUP = "riverside youth";
    private static final String TEXT =
            "Sarah Thompson said the evening paths feel unsafe after dark. More lights near the bus stop would help.";
    private static final String SENTENCE = "More lights near the bus stop would help.";

    /** Keeps saved findings in memory, standing in for MongoDB, and refuses a different record with the same id. */
    private static final class MemoryFindings implements FindingRepository {
        final List<CodedFinding> saved = new ArrayList<>();

        @Override
        public CodedFinding save(CodedFinding finding) {
            Optional<CodedFinding> existing = findByItemId(finding.itemId());
            if (existing.isPresent()) {
                if (existing.get().equals(finding)) return finding;
                throw new IllegalStateException("A different finding already uses this itemId");
            }
            saved.add(finding);
            return finding;
        }

        @Override
        public Optional<CodedFinding> findByItemId(String itemId) {
            return saved.stream().filter(f -> f.itemId().equals(itemId)).findFirst();
        }

        @Override
        public FindingPage findBySourceRef(String sourceRef, int page, int size) {
            return new FindingPage(saved.stream().filter(f -> f.sourceRef().equals(sourceRef)).toList(), page, size, false);
        }
    }

    private final ExceptionsQueueService queue = new ExceptionsQueueService();
    private final InMemorySubmissionStore store = new InMemorySubmissionStore();
    private final MemoryFindings findings = new MemoryFindings();
    private final GroupTierService tiers = new GroupTierService(new InMemoryGroupTierStore());
    private final IntakeService intake = new IntakeService(tiers, store,
            new DefaultPipelineRunner(new PipelineService(text -> List.of()), Set.of(), Set.of(), findings), queue);
    private final FacilitatorCodingService coding = new FacilitatorCodingService(queue, store, tiers, findings);

    FacilitatorCodingTest() {
        intake.classifyGroup(new TierRequest(GROUP, "Riverside Youth", 2, "Test coordinator", "Synthetic test", null));
    }

    private SubmissionReceipt submit() {
        return intake.submit(new SubmissionRequest(List.of(GROUP), TEXT));
    }

    private ExceptionItem itemOf(SubmissionReceipt receipt) {
        List<ExceptionItem> items = queue.listBySource(IntakeService.SOURCE_TYPE, receipt.id());
        assertEquals(1, items.size());
        assertEquals(FlagType.AI_COULD_NOT_CODE, items.get(0).getFlagType());
        return items.get(0);
    }

    private void assertNothingChanged(ExceptionItem item, SubmissionReceipt receipt) {
        assertEquals(ReviewStatus.PENDING, item.getStatus(), "the item must still be waiting");
        assertEquals(SubmissionStatus.PENDING_REVIEW, intake.getReceipt(receipt.id()).orElseThrow().status());
        assertTrue(findings.saved.isEmpty(), "nothing may be saved");
    }

    @Test
    void facilitatorCodingIsSavedAndTheItemIsCleared() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);

        ExceptionItem result = coding.code(item.getId(), "  Lighting and safety  ", SENTENCE, "Clear request for lights")
                .orElseThrow();

        assertEquals(ReviewStatus.CLEARED, result.getStatus());
        assertEquals("Clear request for lights", result.getReviewerNote());
        assertEquals(1, findings.saved.size());
        CodedFinding finding = findings.saved.get(0);
        assertEquals("facilitator", finding.codedBy());
        assertEquals("Lighting and safety", finding.theme(), "the theme is trimmed");
        assertEquals(SENTENCE, finding.quote());
        assertNull(finding.confidence(), "there is no AI confidence for a facilitator's coding");
        assertNull(finding.speakerCode());
        assertEquals(receipt.id(), finding.sourceRef(), "reports find the finding through its submission");
        assertEquals(2, finding.tier());
        assertTrue(finding.flags().isEmpty());
    }

    @Test
    void codingDoesNotPublishOnItsOwnButThePublishStepThenWorks() {
        SubmissionReceipt receipt = submit();
        coding.code(itemOf(receipt).getId(), "Lighting and safety", SENTENCE, "");

        assertEquals(SubmissionStatus.CLEARED, intake.getReceipt(receipt.id()).orElseThrow().status());
        assertTrue(intake.listPublished(GROUP).isEmpty(), "publishing stays a separate step");

        assertEquals(SubmissionStatus.PUBLISHED, intake.publish(receipt.id()).orElseThrow().status());
        assertEquals(1, intake.listPublished(GROUP).size());
    }

    @Test
    void aQuoteThatIsNotInTheIdeaIsRefused() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> coding.code(item.getId(), "Lighting", "Everyone wants more lights.", ""));
        assertTrue(error.getMessage().contains("copied exactly"));
        assertNothingChanged(item, receipt);
    }

    @Test
    void theNameRemovedByDeIdentificationCannotBeQuotedBackIn() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);

        assertThrows(IllegalArgumentException.class,
                () -> coding.code(item.getId(), "Safety", "Sarah Thompson said the evening paths feel unsafe after dark.", ""));
        assertNothingChanged(item, receipt);
    }

    @Test
    void aThemeIsRequiredAndKeptShort() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);

        assertThrows(IllegalArgumentException.class, () -> coding.code(item.getId(), "   ", SENTENCE, ""));
        assertThrows(IllegalArgumentException.class, () -> coding.code(item.getId(), null, SENTENCE, ""));
        assertThrows(IllegalArgumentException.class, () -> coding.code(item.getId(), "x".repeat(81), SENTENCE, ""));
        assertThrows(IllegalArgumentException.class, () -> coding.code(item.getId(), "Lighting", " ", ""));
        assertThrows(IllegalArgumentException.class, () -> coding.code(item.getId(), "Lighting", SENTENCE, "x".repeat(501)));
        assertNothingChanged(item, receipt);
    }

    @Test
    void otherFlagsStillUseClearOrReject() {
        ExceptionItem other = queue.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "a quote", "context", 0.3, "SRC-1"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> coding.code(other.getId(), "Theme", "a quote", ""));
        assertTrue(error.getMessage().contains("Clear or Reject"));
        assertEquals(ReviewStatus.PENDING, other.getStatus());
        assertTrue(findings.saved.isEmpty());
    }

    @Test
    void anUnknownItemIsNotFound() {
        assertTrue(coding.code("no-such-item", "Theme", SENTENCE, "").isEmpty());
    }

    @Test
    void sendingTheSameCodingAgainIsNotAnError() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);
        coding.code(item.getId(), "Lighting", SENTENCE, "");

        assertEquals(ReviewStatus.CLEARED, coding.code(item.getId(), "Lighting", SENTENCE, "").orElseThrow().getStatus());
        assertEquals(1, findings.saved.size());
    }

    @Test
    void anItemThatWasAlreadyReviewedCannotBeCodedDifferently() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);
        queue.reject(item.getId(), "Not about this commission");

        assertThrows(ReviewBlockedException.class, () -> coding.code(item.getId(), "Lighting", SENTENCE, ""));
        assertTrue(findings.saved.isEmpty());
        assertEquals(ReviewStatus.REJECTED, item.getStatus());
    }

    @Test
    void anIdeaWhoseGroupBecameTier3CannotBeCoded() {
        SubmissionReceipt receipt = submit();
        ExceptionItem item = itemOf(receipt);
        Long version = tiers.get(GROUP).orElseThrow().getVersion();
        intake.classifyGroup(new TierRequest(GROUP, "Riverside Youth", 3, "Test coordinator", "Now restricted", version));

        assertThrows(ReviewBlockedException.class, () -> coding.code(item.getId(), "Lighting", SENTENCE, ""));
        assertTrue(findings.saved.isEmpty(), "nothing is saved for a Tier 3 group");
        assertNotEquals(SubmissionStatus.PUBLISHED, intake.getReceipt(receipt.id()).orElseThrow().status());
    }

    // ---- CodedFinding: who coded it ----

    @Test
    void existingAiFindingsAreMarkedAsCodedByTheAi() {
        CodedFinding ai = new CodedFinding("F-1", "S-1", null, "Theme", "quote", 0.8, 2, List.of());
        assertEquals("ai", ai.codedBy());
        assertEquals("ai", ai.withFlags(List.of("lowConfidence")).codedBy());
    }

    @Test
    void onlyAFacilitatorFindingMayHaveNoConfidence() {
        assertThrows(IllegalArgumentException.class,
                () -> new CodedFinding("F-1", "S-1", null, "Theme", "quote", null, 2, List.of()));
        assertNull(CodedFinding.codedByFacilitator("F-2", "S-1", "Theme", "quote", 2).confidence());
        assertThrows(IllegalArgumentException.class,
                () -> new CodedFinding("F-3", "S-1", null, "Theme", "quote", 0.5, 2, List.of(), "someone"));
        assertThrows(IllegalArgumentException.class,
                () -> CodedFinding.codedByFacilitator("F-4", "S-1", "Theme", "quote", 3), "Tier 3 is never coded");
    }
}
