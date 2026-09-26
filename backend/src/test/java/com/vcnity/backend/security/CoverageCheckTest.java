package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CoverageCheckTest {
    @Test
    void allSpeakersCovered() {
        Set<String> speakers = Set.of("P1", "P2");
        List<CodedItem> items = List.of(new CodedItem("i1", "P1"), new CodedItem("i2", "P2"));
        CoverageResult result = CoverageCheck.checkCoverage(speakers, items);
        assertTrue(result.isComplete());
        assertTrue(result.missingSpeakers().isEmpty());
    }
    @Test
    void oneSpeakerMissing() {
        Set<String> speakers = Set.of("P1", "P2", "P3");
        List<CodedItem> items = List.of(new CodedItem("i1", "P1"), new CodedItem("i2", "P2"));
        CoverageResult result = CoverageCheck.checkCoverage(speakers, items);
        assertFalse(result.isComplete());
        assertEquals(Set.of("P3"), result.missingSpeakers());
    }
    @Test
    void multipleSpeakersMissing() {
        Set<String> speakers = Set.of("P1", "P2", "P3", "P4");
        List<CodedItem> items = List.of(new CodedItem("i1", "P1"));
        CoverageResult result = CoverageCheck.checkCoverage(speakers, items);
        assertFalse(result.isComplete());
        assertEquals(Set.of("P2", "P3", "P4"), result.missingSpeakers());
    }
    @Test
    void nullSpeakerCodesThrows() {
        assertThrows(IllegalArgumentException.class, () -> CoverageCheck.checkCoverage(null, List.of()));
    }

    @Test
    void nullOutputItemsThrows() {
        assertThrows(IllegalArgumentException.class, () -> CoverageCheck.checkCoverage(Set.of("P1"), null));
    }
    @Test
    void emptySpeakerSetIsTriviallyComplete() {
        CoverageResult result = CoverageCheck.checkCoverage(Set.of(), List.of());
        assertTrue(result.isComplete());
    }
    @Test
    void mismatchedCasingStillCounted() {
        Set<String> speakers = Set.of("P1");
        List<CodedItem> items = List.of(new CodedItem("i1", "p1"));
        CoverageResult result = CoverageCheck.checkCoverage(speakers, items);
        assertTrue(result.isComplete());
    }
 }