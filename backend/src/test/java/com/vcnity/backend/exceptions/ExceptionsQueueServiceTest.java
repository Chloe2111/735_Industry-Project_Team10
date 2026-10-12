package com.vcnity.backend.exceptions;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsQueueServiceTest {
    private final ExceptionsQueueService service = new ExceptionsQueueService();

    @Test void supportsAllRequiredFlagTypes() {
        for (FlagType type : FlagType.values()) {
            ExceptionItem item = service.add(new ExceptionItem(null, type, "quote", "context", 0.5, "SRC-1"));
            assertEquals(type, item.getFlagType());
            assertEquals(ReviewStatus.PENDING, item.getStatus());
        }
        assertEquals(FlagType.values().length, service.listPending().size());
    }

    @Test void clearAndRejectUpdateHumanReviewStatus() {
        ExceptionItem clearMe = service.add(new ExceptionItem("A", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SRC-A"));
        ExceptionItem rejectMe = service.add(new ExceptionItem("B", FlagType.SOURCE_MISSING, "", "", null, "SRC-B"));
        assertEquals(ReviewStatus.CLEARED, service.clear(clearMe.getId(), "Verified against source").orElseThrow().getStatus());
        assertEquals(ReviewStatus.REJECTED, service.reject(rejectMe.getId(), "No traceable source").orElseThrow().getStatus());
        assertEquals(0, service.listPending().size());
    }

    @Test void invalidIdReturnsEmptyAndInvalidConfidenceFails() {
        assertTrue(service.clear("missing", "").isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                service.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 1.5, "SRC")));
    }

    @Test void rejectRequiresANote() {
        ExceptionItem item = service.add(new ExceptionItem("R", FlagType.CONTESTED, "q", "c", 0.5, "SRC-R"));
        assertThrows(IllegalArgumentException.class, () -> service.reject("R", "   "));
        assertEquals(ReviewStatus.PENDING, service.get("R").orElseThrow().getStatus());
        assertEquals(ReviewStatus.CLEARED, service.clear("R", "").orElseThrow().getStatus());
    }

    @Test void mapsTierViolationAndSkipsDuplicatePendingFlags() {
        assertEquals(List.of(FlagType.TIER_VIOLATION), service.mapReason("tierViolation"));
        PipelineService.PipelineOutcome outcome = new PipelineService.PipelineOutcome(
                "WS-009", "exceptions_queue", "tierViolation", "t", "q", 0.7, 0);
        assertEquals(1, service.addFromPipelineOutcome(outcome, "ctx").size());
        assertEquals(0, service.addFromPipelineOutcome(outcome, "ctx").size());
    }

    @Test void mapsExistingGroundingNamesToStory19Flags() {
        List<FlagType> flags = service.mapReason("quoteNotGrounded, lowConfidence, sourceMissing, contradiction");
        assertEquals(List.of(FlagType.QUOTE_NOT_FOUND, FlagType.LOW_CONFIDENCE, FlagType.SOURCE_MISSING, FlagType.CONTESTED), flags);
    }

    @Test void importsPipelineExceptionWithoutChangingSecurityModule() {
        PipelineService.PipelineOutcome outcome = new PipelineService.PipelineOutcome(
                "WS-006", "exceptions_queue", "quoteNotGrounded", "Session length",
                "fabricated quote", 0.88, 0);
        List<ExceptionItem> imported = service.addFromPipelineOutcome(outcome, "real source text");
        assertEquals(1, imported.size());
        assertEquals(FlagType.QUOTE_NOT_FOUND, imported.get(0).getFlagType());
        assertEquals("WS-006", imported.get(0).getSourceRef());
    }

    // --- Source tagging and review listeners (lets other features share the queue safely) ---

    @Test void listenerIsToldBeforeTheNewStatusIsStored() {
        List<String> seen = new ArrayList<>();
        service.addReviewListener((item, newStatus) ->
                seen.add(item.getId() + ":" + item.getStatus() + "->" + newStatus));
        service.add(new ExceptionItem("L1", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SRC-L1"));
        service.add(new ExceptionItem("L2", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SRC-L2"));

        service.clear("L1", "");
        service.reject("L2", "Not supported by the source");

        assertEquals(List.of("L1:PENDING->CLEARED", "L2:PENDING->REJECTED"), seen);
    }

    @Test void failingListenerLeavesTheItemPendingSoTheReviewCanBeRetried() {
        service.addReviewListener((item, newStatus) -> { throw new IllegalStateException("follow-up failed"); });
        service.add(new ExceptionItem("F1", FlagType.CONTESTED, "q", "c", 0.5, "SRC-F1"));

        assertThrows(IllegalStateException.class, () -> service.clear("F1", ""));

        ExceptionItem item = service.get("F1").orElseThrow();
        assertEquals(ReviewStatus.PENDING, item.getStatus());
        assertNull(item.getReviewedAt());
    }

    @Test void listenerThatFiltersBySourceTypeIgnoresOtherFeaturesItems() {
        List<String> acted = new ArrayList<>();
        service.addReviewListener((item, newStatus) -> {
            if (!"COMMUNITY_SUBMISSION".equals(item.getSourceType())) return;
            acted.add(item.getSourceId());
        });

        // A Story 18 coding finding: no source tag, and it deliberately reuses the same sourceRef.
        ExceptionItem coding = service.add(new ExceptionItem("C1", FlagType.QUOTE_NOT_FOUND, "q", "c", 0.9, "SUB-1"));
        ExceptionItem post = new ExceptionItem("P1", FlagType.QUOTE_NOT_FOUND, "q", "c", 0.9, "SUB-1");
        post.setSourceType("COMMUNITY_SUBMISSION");
        post.setSourceId("SUB-1");
        service.add(post);

        assertEquals(ReviewStatus.CLEARED, service.clear(coding.getId(), "").orElseThrow().getStatus());
        assertTrue(acted.isEmpty(), "clearing a coding finding must not trigger community actions");

        service.clear("P1", "");
        assertEquals(List.of("SUB-1"), acted);
    }

    @Test void listBySourceReturnsOnlyThatRecordsItems() {
        ExceptionItem a = new ExceptionItem("A1", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-1");
        a.setSourceType("COMMUNITY_SUBMISSION");
        a.setSourceId("SUB-1");
        ExceptionItem b = new ExceptionItem("B1", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-2");
        b.setSourceType("COMMUNITY_SUBMISSION");
        b.setSourceId("SUB-2");
        service.add(a);
        service.add(b);
        service.add(new ExceptionItem("U1", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-1"));

        assertEquals(List.of("A1"), service.listBySource("COMMUNITY_SUBMISSION", "SUB-1").stream().map(ExceptionItem::getId).toList());
        assertTrue(service.listBySource(null, "SUB-1").isEmpty());
    }

    @Test
    void mapsAndImportsTierViolationForHumanReview() {
        assertEquals(
                List.of(FlagType.TIER_VIOLATION),
                service.mapReason("tierViolation")
        );

        PipelineService.PipelineOutcome outcome = new PipelineService.PipelineOutcome(
                "SRC-TIER", "exceptions_queue", "tierViolation",
                "Community feedback", "Synthetic quotation", 0.8, 0
        );

        List<ExceptionItem> imported =
                service.addFromPipelineOutcome(outcome, "Synthetic source context");

        assertEquals(1, imported.size());
        assertEquals(FlagType.TIER_VIOLATION, imported.get(0).getFlagType());
        assertEquals(ReviewStatus.PENDING, imported.get(0).getStatus());
        assertEquals("SRC-TIER", imported.get(0).getSourceRef());
    }
}
