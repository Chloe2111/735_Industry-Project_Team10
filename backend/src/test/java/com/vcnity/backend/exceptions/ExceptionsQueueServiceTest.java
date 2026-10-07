package com.vcnity.backend.exceptions;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.junit.jupiter.api.Test;

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
        assertEquals(4, service.listPending().size());
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
}
