package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BatchCodingJobTest {

    private CodingSource source(String reference, String text) {
        return new CodingSource(
                reference,
                text,
                1,
                "speaker_001"
        );
    }

    @Test
    void processesMultipleSourcesAndPreservesMetadata()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Feedback", text, 0.90)
        );

        BatchCodingJob job = new BatchCodingJob(coder);

        CodingSource first = source(
                "source_001",
                "We need more seating."
        );

        CodingSource second = new CodingSource(
                "source_002",
                "The entrance needs better lighting.",
                2,
                "speaker_002"
        );

        BatchCodingJob.BatchResult result =
                job.run(List.of(first, second));

        assertEquals(2, result.outcomes().size());

        for (int index = 0; index < 2; index++) {
            CodingSource expected = List.of(first, second).get(index);
            BatchCodingJob.SourceOutcome outcome =
                    result.outcomes().get(index);

            assertEquals(
                    BatchCodingJob.Status.CHECKS_PASSED,
                    outcome.status()
            );

            assertEquals(expected.sourceRef(), outcome.sourceRef());
            assertNull(outcome.errorCode());
            assertEquals(1, outcome.findings().size());

            CodedFinding finding = outcome.findings().get(0);

            assertEquals(expected.sourceRef(), finding.sourceRef());
            assertEquals(expected.speakerCode(), finding.speakerCode());
            assertEquals(expected.tier(), finding.tier());
            assertEquals(expected.text(), finding.quote());
            assertFalse(finding.itemId().isBlank());
            assertTrue(finding.flags().isEmpty());
        }

        assertNotEquals(
                result.outcomes().get(0).findings().get(0).itemId(),
                result.outcomes().get(1).findings().get(0).itemId()
        );
    }

    @Test
    void supportsMultipleFindingsFromOneSource()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of(
                new CodingDraft(
                        "Seating",
                        "We need more seating.",
                        0.90
                ),
                new CodingDraft(
                        "Lighting",
                        "The entrance is dark.",
                        0.85
                )
        );

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating. The entrance is dark."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(
                BatchCodingJob.Status.CHECKS_PASSED,
                outcome.status()
        );

        assertEquals(2, outcome.findings().size());

        assertNotEquals(
                outcome.findings().get(0).itemId(),
                outcome.findings().get(1).itemId()
        );
    }

    @Test
    void fabricatedQuoteRequiresReview()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of(
                new CodingDraft(
                        "Parking",
                        "We need more parking.",
                        0.90
                )
        );

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(
                BatchCodingJob.Status.REQUIRES_REVIEW,
                outcome.status()
        );

        assertEquals(
                List.of("quoteNotGrounded"),
                outcome.findings().get(0).flags()
        );
    }

    @Test
    void confidenceThresholdHandlesBoundary()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Seating", text, 0.75),
                new CodingDraft("Seating", text, 0.74)
        );

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(
                BatchCodingJob.Status.REQUIRES_REVIEW,
                outcome.status()
        );

        assertTrue(outcome.findings().get(0).flags().isEmpty());

        assertEquals(
                List.of("lowConfidence"),
                outcome.findings().get(1).flags()
        );
    }

    @Test
    void duplicateSourcesAreRejectedBeforeAnyCoding() {
        AtomicInteger calls = new AtomicInteger();

        TranscriptCoder coder = text -> {
            calls.incrementAndGet();
            return List.of();
        };

        BatchCodingJob job = new BatchCodingJob(coder);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.run(List.of(
                        source("same_reference", "First transcript."),
                        source("same_reference", "Second transcript.")
                ))
        );

        assertEquals(0, calls.get());
    }

    @Test
    void nullSourceIsRejectedBeforeAnyCoding() {
        AtomicInteger calls = new AtomicInteger();

        TranscriptCoder coder = text -> {
            calls.incrementAndGet();
            return List.of();
        };

        BatchCodingJob job = new BatchCodingJob(coder);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.run(Arrays.asList(
                        source("source_001", "Valid transcript."),
                        null
                ))
        );

        assertEquals(0, calls.get());
    }

    @Test
    void coderFailureIsRecordedAndNextSourceContinues()
            throws InterruptedException {

        AtomicInteger calls = new AtomicInteger();

        TranscriptCoder coder = text -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException(
                        "Internal details must not appear in the result"
                );
            }

            return List.of(
                    new CodingDraft("Feedback", text, 0.90)
            );
        };

        BatchCodingJob.BatchResult result =
                new BatchCodingJob(coder).run(List.of(
                        source("source_001", "First transcript."),
                        source("source_002", "Second transcript.")
                ));

        assertEquals(2, result.outcomes().size());

        BatchCodingJob.SourceOutcome failed =
                result.outcomes().get(0);

        assertEquals(BatchCodingJob.Status.FAILED, failed.status());
        assertEquals("CODER_FAILED", failed.errorCode());
        assertTrue(failed.findings().isEmpty());

        assertEquals(
                BatchCodingJob.Status.CHECKS_PASSED,
                result.outcomes().get(1).status()
        );
    }

    @Test
    void invalidDraftFailsWholeSourceWithoutPartialFindings()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of(
                new CodingDraft("Seating", text, 0.90),
                new CodingDraft("Seating", text, Double.NaN)
        );

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(BatchCodingJob.Status.FAILED, outcome.status());
        assertEquals("INVALID_CODER_OUTPUT", outcome.errorCode());
        assertTrue(outcome.findings().isEmpty());
    }

    @Test
    void nullCoderOutputIsRecordedAsFailure()
            throws InterruptedException {

        TranscriptCoder coder = text -> null;

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(BatchCodingJob.Status.FAILED, outcome.status());
        assertEquals("INVALID_CODER_OUTPUT", outcome.errorCode());
    }

    @Test
    void emptyCoderOutputIsExplicitlyRecorded()
            throws InterruptedException {

        TranscriptCoder coder = text -> List.of();

        BatchCodingJob.SourceOutcome outcome =
                new BatchCodingJob(coder)
                        .run(List.of(source(
                                "source_001",
                                "We need more seating."
                        )))
                        .outcomes()
                        .get(0);

        assertEquals(
                BatchCodingJob.Status.NO_FINDINGS,
                outcome.status()
        );

        assertTrue(outcome.findings().isEmpty());
        assertNull(outcome.errorCode());
    }

    @Test
    void interruptionStopsBatchAndPreservesInterruptFlag() {
        AtomicInteger calls = new AtomicInteger();

        TranscriptCoder coder = text -> {
            calls.incrementAndGet();
            throw new InterruptedException("Coder interrupted");
        };

        BatchCodingJob job = new BatchCodingJob(coder);

        try {
            assertThrows(
                    InterruptedException.class,
                    () -> job.run(List.of(
                            source("source_001", "First transcript."),
                            source("source_002", "Second transcript.")
                    ))
            );

            assertEquals(1, calls.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear the test thread's flag so other tests can run normally.
            Thread.interrupted();
        }
    }
}