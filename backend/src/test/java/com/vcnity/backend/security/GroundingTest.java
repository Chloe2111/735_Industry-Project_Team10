package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GroundingTest {

    @Test
    void exactQuoteIsGrounded() {
        String source = "The workshop really helped me feel connected to my culture again.";
        assertTrue(Grounding.quoteIsGrounded("feel connected to my culture", source));
    }

    @Test
    void fabricatedFragmentIsNotGrounded() {
        String source = "I felt that a really great sense of belonging came from it.";
        assertFalse(Grounding.quoteIsGrounded("a tarea", source));
    }

    @Test
    void wordBoundaryPreventsPartialWordMatch() {
        String source = "The cat sat on the mat, concatenating nothing at all.";
        assertFalse(Grounding.quoteIsGrounded("catenating", source));
        assertTrue(Grounding.quoteIsGrounded("cat sat", source));
    }

    private Map<String, String> sourceLookup() {
        Map<String, String> m = new HashMap<>();
        m.put("SRC-1", "Participants said the program felt safe and welcoming.");
        return m;
    }

    private Map<String, Object> codedItem(String itemId, String sourceRef, String quote, double confidence, Integer tier) {
        Map<String, Object> m = new HashMap<>();
        m.put("itemId", itemId);
        m.put("sourceRef", sourceRef);
        m.put("quote", quote);
        m.put("confidence", confidence);
        m.put("tier", tier);
        return m;
    }

    @Test
    void cleanItemPassesAllChecks() {
        Grounding.GroundingResult result = Grounding.ground(
                codedItem("I1", "SRC-1", "felt safe and welcoming", 0.9, 1), sourceLookup());
        assertTrue(result.isClean());
    }

    @Test
    void missingSourceIsFlaggedAndShortCircuits() {
        Grounding.GroundingResult result = Grounding.ground(
                codedItem("I2", "NOPE", "anything", 0.9, 1), sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().sourceMissing);
    }

    @Test
    void fabricatedQuoteRoutesToExceptionsQueue() {
        Grounding.GroundingResult result = Grounding.ground(
                codedItem("I3", "SRC-1", "this exact phrase does not appear anywhere", 0.95, 1), sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().quoteNotGrounded);
    }

    @Test
    void lowConfidenceRoutesToExceptionsQueue() {
        Grounding.GroundingResult result = Grounding.ground(
                codedItem("I4", "SRC-1", "felt safe and welcoming", 0.4, 1), sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().lowConfidence);
    }

    @Test
    void tier3ItemReachingGroundingIsStillCaughtAsDefenseInDepth() {
        Grounding.GroundingResult result = Grounding.ground(
                codedItem("I5", "SRC-1", "felt safe and welcoming", 0.9, 3), sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().tierViolation);
    }
}
