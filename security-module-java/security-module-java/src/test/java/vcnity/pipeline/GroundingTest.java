package vcnity.pipeline;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroundingTest {

    // --- quoteIsGrounded: the specific bug fix ---

    @Test
    public void exactQuoteIsGrounded() {
        String source = "The workshop really helped me feel connected to my culture again.";
        assertTrue(Grounding.quoteIsGrounded("feel connected to my culture", source));
    }

    @Test
    public void quoteGroundedRegardlessOfWhitespaceStyle() {
        String source = "The workshop really   helped me feel\nconnected to my culture again.";
        assertTrue(Grounding.quoteIsGrounded("feel connected to my culture", source));
    }

    @Test
    public void fabricatedFragmentIsNotGrounded() {
        // This is the exact bug the adversarial review found: the old
        // logic stripped ALL whitespace before comparing, so a
        // fabricated phrase could match as a substring spanning
        // unrelated words.
        String source = "I felt that a really great sense of belonging came from it.";
        String fabricated = "a tarea"; // not present as real words; only
        // present as a whitespace-stripped substring of "that a really"
        assertFalse(Grounding.quoteIsGrounded(fabricated, source));
    }

    @Test
    public void wordBoundaryPreventsPartialWordMatch() {
        String source = "The cat sat on the mat, concatenating nothing at all.";
        assertFalse(Grounding.quoteIsGrounded("catenating", source));
        assertTrue(Grounding.quoteIsGrounded("cat sat", source));
    }

    @Test
    public void emptyQuoteIsNotGrounded() {
        assertFalse(Grounding.quoteIsGrounded("", "some source text"));
    }

    // --- ground(): full pipeline behaviour ---

    private Map<String, String> sourceLookup() {
        return Map.of("SRC-1", "Participants said the program felt safe and welcoming.");
    }

    @Test
    public void cleanItemPassesAllChecks() {
        CodedItem codedItem = new CodedItem("I1", "SRC-1", "felt safe and welcoming", 0.9, 1);
        GroundResult result = Grounding.ground(codedItem, sourceLookup());
        assertTrue(result.isClean());
        assertFalse(result.flags().any());
    }

    @Test
    public void missingSourceIsFlaggedAndShortCircuits() {
        // Fixes the second bug the review found: SOURCE_MISSING was
        // unreachable because the source was looked up as if it always
        // existed. Here it's the first check performed.
        CodedItem codedItem = new CodedItem("I2", "SRC-DOES-NOT-EXIST", "anything", 0.9, 1);
        GroundResult result = Grounding.ground(codedItem, sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().sourceMissing());
    }

    @Test
    public void fabricatedQuoteRoutesToExceptionsQueue() {
        CodedItem codedItem = new CodedItem("I3", "SRC-1", "this exact phrase does not appear anywhere", 0.95, 1);
        GroundResult result = Grounding.ground(codedItem, sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().quoteNotGrounded());
    }

    @Test
    public void lowConfidenceRoutesToExceptionsQueue() {
        CodedItem codedItem = new CodedItem("I4", "SRC-1", "felt safe and welcoming", 0.4, 1);
        GroundResult result = Grounding.ground(codedItem, sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().lowConfidence());
    }

    @Test
    public void tier3ItemReachingGroundingIsStillCaughtAsDefenseInDepth() {
        // tierGate() should have already stopped this upstream — but
        // grounding checks it too, as a second, independent layer.
        CodedItem codedItem = new CodedItem("I5", "SRC-1", "felt safe and welcoming", 0.9, 3);
        GroundResult result = Grounding.ground(codedItem, sourceLookup());
        assertFalse(result.isClean());
        assertTrue(result.flags().tierViolation());
    }
}
