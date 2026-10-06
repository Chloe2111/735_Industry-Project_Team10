package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchCodingWorkflowTest {

    private CodingSource source(String reference, String text) {
        return new CodingSource(
                reference,
                text,
                1,
                "speaker_001"
        );
    }

    @Test
    void passingFindingWaitsForVerificationWithoutQueueEntry()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService();

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Seating", text, 0.90)
        );

        BatchCodingWorkflow workflow =
                new BatchCodingWorkflow(coder, queue);

        BatchCodingWorkflow.RoutedSource result = workflow.run(
                List.of(source(
                        "transcript_001",
                        "We need more seating."
                ))
        ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_VERIFICATION,
                result.routingStatus()
        );

        assertEquals(1, result.codingOutcome().findings().size());
        assertTrue(result.queueReceipts().isEmpty());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void fabricatedQuoteReachesQueueAndCanBeReviewed()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService();

        TranscriptCoder coder = text -> List.of(
                new CodingDraft(
                        "Parking",
                        "We need more parking.",
                        0.90
                )
        );

        BatchCodingWorkflow workflow =
                new BatchCodingWorkflow(coder, queue);

        BatchCodingWorkflow.RoutedSource result = workflow.run(
                List.of(source(
                        "transcript_001",
                        "We need more seating."
                ))
        ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_EXCEPTION_REVIEW,
                result.routingStatus()
        );

        assertEquals(1, result.queueReceipts().size());

        BatchCodingWorkflow.FindingReceipt findingReceipt =
                result.queueReceipts().get(0);

        assertEquals(
                result.codingOutcome().findings().get(0).itemId(),
                findingReceipt.itemId()
        );

        assertTrue(findingReceipt.receipt().complete());
        assertEquals(1, queue.listPending().size());

        String entryId =
                findingReceipt.receipt().exceptionIds().get(0);

        ExceptionItem entry = queue.get(entryId).orElseThrow();

        assertEquals(FlagType.QUOTE_NOT_FOUND, entry.getFlagType());
        assertEquals("transcript_001", entry.getSourceRef());
        assertEquals("We need more parking.", entry.getSourceQuote());
        assertEquals("We need more seating.", entry.getSourceContext());
        assertEquals(ReviewStatus.PENDING, entry.getStatus());

        ExceptionItem reviewed = queue.reject(
                entryId,
                "The quote does not occur in the synthetic source."
        ).orElseThrow();

        assertEquals(ReviewStatus.REJECTED, reviewed.getStatus());
        assertNotNull(reviewed.getReviewedAt());
        assertEquals(
                "The quote does not occur in the synthetic source.",
                reviewed.getReviewerNote()
        );

        assertTrue(queue.listPending().isEmpty());
        assertEquals(1, queue.list().size());
    }

    @Test
    void mixedFindingsHoldSourceAndQueueOnlyFlaggedFinding()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService();

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Seating", text, 0.90),
                new CodingDraft(
                        "Parking",
                        "We need more parking.",
                        0.90
                )
        );

        BatchCodingWorkflow.RoutedSource result =
                new BatchCodingWorkflow(coder, queue).run(
                        List.of(source(
                                "transcript_001",
                                "We need more seating."
                        ))
                ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_EXCEPTION_REVIEW,
                result.routingStatus()
        );

        assertEquals(2, result.codingOutcome().findings().size());
        assertEquals(1, result.queueReceipts().size());
        assertEquals(1, queue.list().size());

        assertEquals(
                result.codingOutcome().findings().get(1).itemId(),
                result.queueReceipts().get(0).itemId()
        );
    }

    @Test
    void queueFailureDoesNotReportSuccessfulRouting()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService() {
            @Override
            public ExceptionItem add(ExceptionItem item) {
                throw new IllegalStateException("Simulated write failure");
            }
        };

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Seating", text, 0.50)
        );

        BatchCodingWorkflow.RoutedSource result =
                new BatchCodingWorkflow(coder, queue).run(
                        List.of(source(
                                "transcript_001",
                                "We need more seating."
                        ))
                ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.QUEUE_FAILED,
                result.routingStatus()
        );

        assertEquals(1, result.queueReceipts().size());

        BatchExceptionsAdapter.QueueReceipt receipt =
                result.queueReceipts().get(0).receipt();

        assertFalse(receipt.complete());
        assertEquals("QUEUE_WRITE_FAILED", receipt.errorCode());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void codingFailureRemainsExplicitWithoutQueueEntry()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService();

        TranscriptCoder coder = text -> {
            throw new IllegalStateException("Simulated coder failure");
        };

        BatchCodingWorkflow.RoutedSource result =
                new BatchCodingWorkflow(coder, queue).run(
                        List.of(source(
                                "transcript_001",
                                "We need more seating."
                        ))
                ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.CODING_FAILED,
                result.routingStatus()
        );

        assertEquals(
                "CODER_FAILED",
                result.codingOutcome().errorCode()
        );

        assertTrue(result.queueReceipts().isEmpty());
        assertTrue(queue.list().isEmpty());
    }

    @Test
    void noFindingsRemainsExplicitWithoutQueueEntry()
            throws InterruptedException {

        ExceptionsQueueService queue = new ExceptionsQueueService();
        TranscriptCoder coder = text -> List.of();

        BatchCodingWorkflow.RoutedSource result =
                new BatchCodingWorkflow(coder, queue).run(
                        List.of(source(
                                "transcript_001",
                                "We need more seating."
                        ))
                ).sources().get(0);

        assertEquals(
                BatchCodingWorkflow.RoutingStatus.NO_FINDINGS,
                result.routingStatus()
        );

        assertTrue(result.queueReceipts().isEmpty());
        assertTrue(queue.list().isEmpty());
    }
}