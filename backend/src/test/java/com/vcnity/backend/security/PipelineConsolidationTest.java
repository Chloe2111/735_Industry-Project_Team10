package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PipelineConsolidationTest {

    private static final String SOURCE = "SOURCE-1";
    private static final String TEXT =
            "The workshop was helpful. We need more seating.";

    private PipelineService.PipelineRun run(PipelineService service)
            throws Exception {
        return service.runPipeline(
                List.of(new PipelineService.PipelineItem(
                        SOURCE, 1, TEXT, null
                )),
                Set.of(),
                Set.of()
        );
    }

    @Test
    void preservesMultipleFindingsWithDistinctIdsAndTheirSourceReference()
            throws Exception {
        PipelineService service = new PipelineService(text -> List.of(
                new CodingDraft("Workshop value", "The workshop was helpful.", 0.95),
                new CodingDraft("Seating", "We need more seating.", 0.95)
        ));

        var result = run(service);

        assertEquals(2, result.findings().size());
        assertEquals(2, result.clean().size());
        assertTrue(result.exceptions().isEmpty());

        assertEquals(
                Set.of("The workshop was helpful.", "We need more seating."),
                result.findings().stream()
                        .map(CodedFinding::quote)
                        .collect(java.util.stream.Collectors.toSet())
        );

        assertNotEquals(
                result.findings().get(0).itemId(),
                result.findings().get(1).itemId()
        );

        for (CodedFinding finding : result.findings()) {
            assertEquals(SOURCE, finding.sourceRef());
            assertNull(finding.speakerCode(),
                    "A missing speaker code must not become the source ID");
            assertTrue(finding.flags().isEmpty());
        }
    }

    @Test
    void keepsAnUngroundedFindingAlongsideACleanFinding()
            throws Exception {
        PipelineService service = new PipelineService(text -> List.of(
                new CodingDraft("Workshop value", "The workshop was helpful.", 0.95),
                new CodingDraft("Transport", "Everyone requested a new bus route.", 0.95)
        ));

        var result = run(service);

        assertEquals(2, result.findings().size());
        assertEquals(1, result.clean().size());
        assertEquals(1, result.exceptions().size());
        assertTrue(result.exceptions().get(0).reason().contains("quoteNotGrounded"));

        CodedFinding flagged = result.findings().stream()
                .filter(finding -> finding.flags().contains("quoteNotGrounded"))
                .findFirst().orElseThrow();

        assertEquals("Everyone requested a new bus route.", flagged.quote());
        assertEquals(SOURCE, flagged.sourceRef());
    }

    @Test
    void tierThreeAndUntieredSourcesNeverReachTheCoder() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PipelineService service = new PipelineService(text -> {
            calls.incrementAndGet();
            return List.of(new CodingDraft("Feedback", text, 0.95));
        });

        var result = service.runPipeline(
                List.of(
                        new PipelineService.PipelineItem("RESTRICTED", 3, TEXT, null),
                        new PipelineService.PipelineItem("UNTIERED", null, TEXT, null)
                ),
                Set.of(),
                Set.of()
        );

        assertEquals(0, calls.get());
        assertEquals(2, result.rejected().size());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.clean().isEmpty());
    }

    @Test
    void coderReceivesRedactedTextInsteadOfTheEmailAddress() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        PipelineService service = new PipelineService(text -> {
            received.set(text);
            return List.of(new CodingDraft("Feedback", text, 0.95));
        });

        var result = service.runPipeline(
                List.of(new PipelineService.PipelineItem(
                        SOURCE, 2,
                        "Contact me at synthetic.person@example.com. We need more seating.",
                        null
                )),
                Set.of(),
                Set.of()
        );

        assertNotNull(received.get());
        assertFalse(received.get().contains("synthetic.person@example.com"));
        assertEquals(1, result.findings().size());
        assertEquals(received.get(), result.findings().get(0).quote());
        assertTrue(result.findings().get(0).flags().isEmpty());
    }

    @Test
    void codingFailureProducesAControlledFailureAndNoCleanFinding()
            throws Exception {
        PipelineService service = new PipelineService(text -> {
            throw new IOException("Private provider details");
        });

        var result = run(service);

        assertEquals(1, result.outcomes().size());
        assertEquals("coding_failed", result.outcomes().get(0).stageReached());
        assertEquals("CODER_FAILED", result.outcomes().get(0).reason());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.clean().isEmpty());
    }

    @Test
    void emptyCoderOutputIsExplicitlyReportedAsNoFindings() throws Exception {
        PipelineService service = new PipelineService(text -> List.of());

        var result = run(service);

        assertEquals(1, result.outcomes().size());
        assertEquals("no_findings", result.outcomes().get(0).stageReached());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.clean().isEmpty());
    }

    @Test
    void returnedFindingsCannotBeModifiedByTheCaller() throws Exception {
        PipelineService service = new PipelineService(text -> List.of(
                new CodingDraft("Seating", "We need more seating.", 0.95)
        ));

        var result = run(service);

        assertThrows(UnsupportedOperationException.class,
                () -> result.findings().clear());
    }
}
