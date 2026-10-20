package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BatchFlagContractTest {

    @Test
    void everySharedFindingFlagCreatesAPendingReviewItem() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter = new BatchExceptionsAdapter(queue);

        CodedFinding finding = new CodedFinding(
                "FINDING-1",
                "SOURCE-1",
                null,
                "Community connection",
                "The workshop helped us connect.",
                0.4,
                1,
                List.of(
                        "quoteNotGrounded",
                        "lowConfidence",
                        "sourceMissing",
                        "contradiction",
                        "tierViolation"
                )
        );

        var receipt = adapter.enqueue(
                finding, "The workshop helped us connect."
        );

        assertTrue(receipt.complete());
        assertNull(receipt.errorCode());
        assertEquals(5, receipt.exceptionIds().size());
        assertEquals(5, queue.listPending().size());

        assertEquals(
                Set.of(
                        FlagType.QUOTE_NOT_FOUND,
                        FlagType.LOW_CONFIDENCE,
                        FlagType.SOURCE_MISSING,
                        FlagType.CONTESTED,
                        FlagType.TIER_VIOLATION
                ),
                queue.listPending().stream()
                        .map(item -> item.getFlagType())
                        .collect(Collectors.toSet())
        );

        for (var id : receipt.exceptionIds()) {
            var item = queue.get(id).orElseThrow();
            assertEquals("SOURCE-1", item.getSourceRef());
            assertEquals(ReviewStatus.PENDING, item.getStatus());
        }
    }
}
