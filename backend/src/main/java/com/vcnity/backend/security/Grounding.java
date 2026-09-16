package com.vcnity.backend.security;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding.java
 *
 * The deterministic checks that run on every AI-coded item before it
 * can be marked "clean" and skip human review.
 *
 * Fixes the bug the adversarial review flagged: the original quote-
 * match logic stripped ALL whitespace from both the quote and the
 * source before comparing them as substrings, which let fabricated
 * fragments pass as "grounded" if they matched across word boundaries.
 *
 * The fix below normalises whitespace (collapses, doesn't delete it)
 * and matches on real word boundaries, so a quote only counts as
 * grounded if it appears as an actual contiguous sequence of whole
 * words in the source.
 */
public final class Grounding {

    private static final double CONFIDENCE_THRESHOLD = 0.75;

    private Grounding() {
    }

    /** Collapse whitespace and lowercase. Does NOT delete whitespace — that was the original bug. */
    public static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * True only if {@code quote} appears in {@code sourceText} as a
     * contiguous sequence of whole words (word-boundary match), not as
     * an arbitrary substring.
     */
    public static boolean quoteIsGrounded(String quote, String sourceText) {
        if (quote == null || quote.isEmpty() || sourceText == null || sourceText.isEmpty()) {
            return false;
        }
        String q = normalize(quote);
        String s = normalize(sourceText);
        if (q.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<!\\w)" + Pattern.quote(q) + "(?!\\w)");
        return pattern.matcher(s).find();
    }

    public static final class GroundingFlags {
        public boolean sourceMissing = false;
        public boolean quoteNotGrounded = false;
        public boolean lowConfidence = false;
        public boolean tierViolation = false;
        public boolean contradiction = false;

        public boolean any() {
            return sourceMissing || quoteNotGrounded || lowConfidence || tierViolation || contradiction;
        }

        public Map<String, Boolean> asMap() {
            Map<String, Boolean> m = new HashMap<>();
            m.put("sourceMissing", sourceMissing);
            m.put("quoteNotGrounded", quoteNotGrounded);
            m.put("lowConfidence", lowConfidence);
            m.put("tierViolation", tierViolation);
            m.put("contradiction", contradiction);
            return m;
        }
    }

    public record GroundingResult(String itemId, boolean isClean, GroundingFlags flags) {
    }

    /**
     * codedItem keys expected: itemId, sourceRef, quote, confidence
     * (Double), tier (Integer), contradictsPriorTheme (Boolean,
     * optional).
     * sourceLookup: sourceRef -> full source text, built from the
     * corpus BEFORE coding runs (so a missing source is a real
     * integrity failure, not a lookup-order bug).
     */
    public static GroundingResult ground(Map<String, Object> codedItem, Map<String, String> sourceLookup) {
        String itemId = codedItem.get("itemId") != null ? codedItem.get("itemId").toString() : "<unknown>";
        GroundingFlags flags = new GroundingFlags();

        Object sourceRefObj = codedItem.get("sourceRef");
        String sourceRef = sourceRefObj != null ? sourceRefObj.toString() : null;
        String sourceText = sourceRef != null ? sourceLookup.get(sourceRef) : null;

        // Fix for the second bug the review found: SOURCE_MISSING was
        // unreachable because the original code looked the source up
        // AFTER already assuming it existed. Here the existence check
        // happens first and is the only thing gating the rest.
        if (sourceText == null) {
            flags.sourceMissing = true;
            return new GroundingResult(itemId, false, flags);
        }

        String quote = codedItem.get("quote") != null ? codedItem.get("quote").toString() : "";
        if (!quoteIsGrounded(quote, sourceText)) {
            flags.quoteNotGrounded = true;
        }

        double confidence = codedItem.get("confidence") != null ? ((Number) codedItem.get("confidence")).doubleValue() : 0.0;
        if (confidence < CONFIDENCE_THRESHOLD) {
            flags.lowConfidence = true;
        }

        Object tierObj = codedItem.get("tier");
        if (tierObj != null && ((Number) tierObj).intValue() == 3) {
            flags.tierViolation = true;
        }

        // Contradiction scan is intentionally a hook, not a full NLP
        // model, for this MVP — wire in a real check before the real pilot.
        Object contradicts = codedItem.get("contradictsPriorTheme");
        if (Boolean.TRUE.equals(contradicts)) {
            flags.contradiction = true;
        }

        return new GroundingResult(itemId, !flags.any(), flags);
    }
}
