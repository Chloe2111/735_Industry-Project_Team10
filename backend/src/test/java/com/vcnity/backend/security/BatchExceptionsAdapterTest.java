package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BatchExceptionsAdapterTest {

    private CodedFinding finding(List<String> flags) {
        return new CodedFinding(
                "finding_001",
                "transcript_001",
                "speaker_001",
                "Seating",
                "We need more seating.",
                0.60,
                1,
                flags
        );
    }

    @Test
    void preservesSourceReferenceAndCreatesPendingEntry() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of("lowConfidence")),
                "We need more seating."
        );

        assertTrue(receipt.complete());
        assertNull(receipt.errorCode());
        assertEquals(1, receipt.exceptionIds().size());

        ExceptionItem saved = queue.get(
                receipt.exceptionIds().get(0)
        ).orElseThrow();

        assertEquals("transcript_001", saved.getSourceRef());
        assertNotEquals("finding_001", saved.getSourceRef());
        assertEquals(FlagType.LOW_CONFIDENCE, saved.getFlagType());
        assertEquals(ReviewStatus.PENDING, saved.getStatus());
        assertEquals("We need more seating.", saved.getSourceQuote());
        assertEquals("We need more seating.", saved.getSourceContext());
        assertEquals(Double.valueOf(0.60), saved.getConfidence());
    }

    @Test
    void mapsOriginalFourGroundingFlags() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of(
                        "quoteNotGrounded",
                        "lowConfidence",
                        "sourceMissing",
                        "contradiction"
                )),
                null
        );

        assertTrue(receipt.complete());
        assertEquals(4, receipt.exceptionIds().size());

        Set<FlagType> actualTypes = queue.list()
                .stream()
                .map(ExceptionItem::getFlagType)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        FlagType.QUOTE_NOT_FOUND,
                        FlagType.LOW_CONFIDENCE,
                        FlagType.SOURCE_MISSING,
                        FlagType.CONTESTED
                ),
                actualTypes
        );
    }

    @Test
    void unknownFlagIsRejectedBeforeAnyQueueWrite() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter = new BatchExceptionsAdapter(queue);

        assertThrows(IllegalArgumentException.class, () ->
                adapter.enqueue(
                        finding(List.of("lowConfidence", "UNKNOWN_TEST_FLAG")),
                        "We need more seating."
                ));

        assertTrue(queue.list().isEmpty());
    }

    @Test
    void missingContextIsRejectedWhenSourceIsNotMissing() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of("lowConfidence")),
                " "
        );

        assertFalse(receipt.complete());
        assertEquals("SOURCE_CONTEXT_REQUIRED", receipt.errorCode());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void noFlagsCreatesNoQueueEntries() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of()),
                "We need more seating."
        );

        assertTrue(receipt.complete());
        assertTrue(receipt.exceptionIds().isEmpty());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void queueFailureReturnsControlledError() {
        ExceptionsQueueService queue = new ExceptionsQueueService() {
            @Override
            public ExceptionItem add(ExceptionItem item) {
                throw new IllegalStateException(
                        "Internal storage details must not be exposed"
                );
            }
        };

        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of("lowConfidence")),
                "We need more seating."
        );

        assertFalse(receipt.complete());
        assertEquals("QUEUE_WRITE_FAILED", receipt.errorCode());
        assertTrue(receipt.exceptionIds().isEmpty());
    }

    @Test
    void partialFailureRetainsEarlierEntryIds() {
        ExceptionsQueueService queue = new ExceptionsQueueService() {
            private int writes = 0;

            @Override
            public ExceptionItem add(ExceptionItem item) {
                writes++;

                if (writes == 2) {
                    throw new IllegalStateException("Second write failed");
                }

                return super.add(item);
            }
        };

        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of("quoteNotGrounded", "lowConfidence")),
                "The entrance needs better lighting."
        );

        assertFalse(receipt.complete());
        assertEquals("QUEUE_WRITE_FAILED", receipt.errorCode());
        assertEquals(1, receipt.exceptionIds().size());
        assertEquals(1, queue.list().size());

        assertTrue(
                queue.get(receipt.exceptionIds().get(0)).isPresent()
        );
    }

    @Test
    void queuedItemCanBeClearedForVerification() {
        ExceptionsQueueService queue = new ExceptionsQueueService();
        BatchExceptionsAdapter adapter =
                new BatchExceptionsAdapter(queue);

        BatchExceptionsAdapter.QueueReceipt receipt = adapter.enqueue(
                finding(List.of("lowConfidence")),
                "We need more seating."
        );

        String entryId = receipt.exceptionIds().get(0);

        ExceptionItem reviewed = queue.clear(
                entryId,
                "Quote checked against the synthetic source."
        ).orElseThrow();

        assertEquals(ReviewStatus.CLEARED, reviewed.getStatus());
        assertEquals(
                "Quote checked against the synthetic source.",
                reviewed.getReviewerNote()
        );
        assertNotNull(reviewed.getReviewedAt());
        assertTrue(queue.listPending().isEmpty());
        assertEquals(1, queue.list().size());
    }
}