package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PipelineSpeakerCoverageTest {

    private PipelineService.PipelineItem source(
            String id, String text, String speaker) {
        return new PipelineService.PipelineItem(id, 1, text, null, speaker);
    }

    private PipelineService codingEverySource() {
        return new PipelineService(text ->
                List.of(new CodingDraft("Workshop feedback", text, 0.95)));
    }

    @Test
    void knownSpeakerIsPreservedAndCoverageIsComplete() throws Exception {
        var result = codingEverySource().runPipeline(
                List.of(source("SRC-1", "The workshop was helpful.", "SPEAKER-A")),
                Set.of(), Set.of()
        );

        assertEquals(PipelineService.CoverageStatus.COMPLETE,
                result.coverage().status());
        assertTrue(result.coverage().missingSpeakers().isEmpty());
        assertTrue(result.coverage().sourcesWithoutSpeakerCodes().isEmpty());
        assertEquals("SPEAKER-A", result.findings().get(0).speakerCode());
        assertEquals("SRC-1", result.findings().get(0).sourceRef());
    }

    @Test
    void unknownSpeakerMeansCoverageWasNotAssessed() throws Exception {
        var result = codingEverySource().runPipeline(
                List.of(source("SRC-UNKNOWN", "The workshop was helpful.", null)),
                Set.of(), Set.of()
        );

        assertEquals(PipelineService.CoverageStatus.NOT_ASSESSED,
                result.coverage().status());
        assertEquals(Set.of("SRC-UNKNOWN"),
                result.coverage().sourcesWithoutSpeakerCodes());
        assertNull(result.findings().get(0).speakerCode());
    }

    @Test
    void mixedKnownAndUnknownSpeakersMeansPartialCoverage() throws Exception {
        var result = codingEverySource().runPipeline(
                List.of(
                        source("SRC-KNOWN", "The workshop was helpful.", "SPEAKER-A"),
                        source("SRC-UNKNOWN", "We need more seating.", null)
                ),
                Set.of(), Set.of()
        );

        assertEquals(PipelineService.CoverageStatus.PARTIAL,
                result.coverage().status());
        assertTrue(result.coverage().missingSpeakers().isEmpty());
        assertEquals(Set.of("SRC-UNKNOWN"),
                result.coverage().sourcesWithoutSpeakerCodes());
    }

    @Test
    void missingSpeakerCreatesASourceLinkedPendingQueueItem() throws Exception {
        PipelineService service = new PipelineService(text ->
                text.contains("seating")
                        ? List.of()
                        : List.of(new CodingDraft("Workshop feedback", text, 0.95)));

        var result = service.runPipeline(
                List.of(
                        source("SRC-A", "The workshop was helpful.", "SPEAKER-A"),
                        source("SRC-B", "We need more seating.", "SPEAKER-B")
                ),
                Set.of(), Set.of()
        );

        assertEquals(PipelineService.CoverageStatus.GAPS,
                result.coverage().status());
        assertEquals(Set.of("SPEAKER-B"), result.coverage().missingSpeakers());
        assertEquals(1, result.findings().size());
        assertEquals(1, result.exceptions().size());

        var gap = result.exceptions().get(0);
        assertEquals("SRC-B", gap.itemId());
        assertEquals("REPRESENTATION_GAP", gap.reason());
        assertNull(gap.quote(), "Coverage gaps must not invent evidence quotations");
        assertTrue(result.clean().stream()
                .noneMatch(outcome -> "SRC-B".equals(outcome.itemId())));

        ExceptionsQueueService queue = new ExceptionsQueueService();
        var imported = queue.addFromPipelineOutcome(
                gap,
                "An expected speaker has no coded findings. Review source coverage."
        );

        assertEquals(1, imported.size());
        assertEquals(FlagType.REPRESENTATION_GAP, imported.get(0).getFlagType());
        assertEquals("SRC-B", imported.get(0).getSourceRef());
        assertEquals(ReviewStatus.PENDING, imported.get(0).getStatus());
    }

    @Test
    void anotherFindingFromTheSameSpeakerSatisfiesSpeakerCoverage()
            throws Exception {
        PipelineService service = new PipelineService(text ->
                text.contains("seating")
                        ? List.of()
                        : List.of(new CodingDraft("Workshop feedback", text, 0.95)));

        var result = service.runPipeline(
                List.of(
                        source("SRC-A", "The workshop was helpful.", " speaker-a "),
                        source("SRC-B", "We need more seating.", "SPEAKER-A")
                ),
                Set.of(), Set.of()
        );

        assertEquals(PipelineService.CoverageStatus.COMPLETE,
                result.coverage().status());
        assertTrue(result.coverage().missingSpeakers().isEmpty());
        assertTrue(result.exceptions().isEmpty());
        assertTrue(result.outcomes().stream().anyMatch(outcome ->
                "SRC-B".equals(outcome.itemId())
                        && "no_findings".equals(outcome.stageReached())));
    }

    @Test
    void restrictedSourceIsExcludedFromExpectedSpeakerCoverage()
            throws Exception {
        var result = codingEverySource().runPipeline(
                List.of(
                        source("SRC-A", "The workshop was helpful.", "SPEAKER-A"),
                        new PipelineService.PipelineItem(
                                "SRC-RESTRICTED", 3, "Restricted material.",
                                null, "SPEAKER-RESTRICTED")
                ),
                Set.of(), Set.of()
        );

        assertEquals(1, result.rejected().size());
        assertEquals(1, result.findings().size());
        assertEquals(PipelineService.CoverageStatus.COMPLETE,
                result.coverage().status());
        assertTrue(result.coverage().missingSpeakers().isEmpty());
    }
}
