package com.vcnity.backend.exceptions;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionSourceValidator;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
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

    // --- A blocked item is never reported as cleared ---

    @Test void blockedItemCannotBeClearedAndTheReviewerIsToldWhy() {
        ExceptionItem item = new ExceptionItem("H1", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SRC-H1");
        item.setBlockedReason("Halted: the group is now Tier 3.");
        service.add(item);

        ReviewBlockedException blocked = assertThrows(ReviewBlockedException.class, () -> service.clear("H1", ""));

        assertEquals(409, blocked.getStatus());
        assertEquals("Halted: the group is now Tier 3.", blocked.getMessage());
        assertEquals(ReviewStatus.PENDING, service.get("H1").orElseThrow().getStatus());
        assertNull(service.get("H1").orElseThrow().getReviewedAt());
    }

    @Test void blockedItemCanStillBeRejected() {
        ExceptionItem item = new ExceptionItem("H2", FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SRC-H2");
        item.setBlockedReason("Halted: the group is now Tier 3.");
        service.add(item);

        assertEquals(ReviewStatus.REJECTED, service.reject("H2", "Not needed any more").orElseThrow().getStatus());
    }

    // --- Items posted through the public API ---

    private ExceptionSourceValidator knownSubmissions(String... ids) {
        return new ExceptionSourceValidator() {
            @Override public String sourceType() { return "COMMUNITY_SUBMISSION"; }
            @Override public void validateSource(String sourceId) {
                if (!List.of(ids).contains(sourceId)) {
                    throw new IllegalArgumentException("No submission " + sourceId + " is waiting for review");
                }
            }
        };
    }

    @Test void addingAnIdThatAlreadyExistsIsRefusedSoAnItemCannotBeOverwritten() {
        service.add(new ExceptionItem("DUP", FlagType.LOW_CONFIDENCE, "original", "c", 0.4, "SRC-1"));
        service.clear("DUP", "");

        assertThrows(IllegalArgumentException.class,
                () -> service.add(new ExceptionItem("DUP", FlagType.CONTESTED, "replacement", "c", 0.4, "SRC-1")));

        ExceptionItem kept = service.get("DUP").orElseThrow();
        assertEquals("original", kept.getSourceQuote());
        assertEquals(ReviewStatus.CLEARED, kept.getStatus());
    }

    @Test void postedItemCannotChooseItsIdOrPreFillTheReview() {
        service.add(new ExceptionItem("EXISTING", FlagType.LOW_CONFIDENCE, "original", "c", 0.4, "SRC-1"));
        ExceptionItem posted = new ExceptionItem("EXISTING", FlagType.CONTESTED, "posted", "c", 0.5, "SRC-2");
        posted.setStatus(ReviewStatus.CLEARED);
        posted.setReviewerNote("already checked, trust me");
        posted.setBlockedReason("fake");

        ExceptionItem created = service.addExternal(posted);

        assertNotEquals("EXISTING", created.getId());
        assertEquals(ReviewStatus.PENDING, created.getStatus());
        assertNull(created.getReviewerNote());
        assertNull(created.getBlockedReason());
        assertEquals("original", service.get("EXISTING").orElseThrow().getSourceQuote());
    }

    @Test void postedItemWithASourceTagMustNameARealRecord() {
        service.addSourceValidator(knownSubmissions("SUB-1"));

        ExceptionItem real = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-1");
        real.setSourceType("COMMUNITY_SUBMISSION");
        real.setSourceId("SUB-1");
        assertEquals("SUB-1", service.addExternal(real).getSourceId());

        ExceptionItem invented = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-999");
        invented.setSourceType("COMMUNITY_SUBMISSION");
        invented.setSourceId("SUB-999");
        assertThrows(IllegalArgumentException.class, () -> service.addExternal(invented));

        assertEquals(1, service.list().size());
    }

    @Test void postedItemWithAnUnknownOrHalfSourceTagIsRefused() {
        service.addSourceValidator(knownSubmissions("SUB-1"));

        ExceptionItem unknownType = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "X");
        unknownType.setSourceType("SOMETHING_ELSE");
        unknownType.setSourceId("X");
        assertThrows(IllegalArgumentException.class, () -> service.addExternal(unknownType));

        ExceptionItem typeOnly = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "X");
        typeOnly.setSourceType("COMMUNITY_SUBMISSION");
        assertThrows(IllegalArgumentException.class, () -> service.addExternal(typeOnly));

        ExceptionItem idOnly = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "X");
        idOnly.setSourceId("SUB-1");
        assertThrows(IllegalArgumentException.class, () -> service.addExternal(idOnly));

        assertThrows(IllegalArgumentException.class, () -> service.addExternal(null));
        assertTrue(service.list().isEmpty());
    }

    @Test void postedItemWithoutASourceTagIsAcceptedLikeBefore() {
        ExceptionItem untagged = new ExceptionItem(null, FlagType.QUOTE_NOT_FOUND, "q", "c", 0.9, "transcript_04.txt");

        ExceptionItem created = service.addExternal(untagged);

        assertNotNull(created.getId());
        assertNull(created.getSourceType());
        assertEquals(ReviewStatus.PENDING, created.getStatus());
    }

    @Test void sourceIdsListsEveryRecordOfOneFeatureThatHasItems() {
        ExceptionItem a = new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-1");
        a.setSourceType("COMMUNITY_SUBMISSION");
        a.setSourceId("SUB-1");
        service.add(a);
        service.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE, "q", "c", 0.4, "SUB-2"));

        assertEquals(java.util.Set.of("SUB-1"), service.sourceIds("COMMUNITY_SUBMISSION"));
        assertTrue(service.sourceIds("OTHER").isEmpty());
    }
}
