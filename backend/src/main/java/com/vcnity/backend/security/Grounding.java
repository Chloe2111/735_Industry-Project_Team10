package com.vcnity.backend.security;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic checks for coded findings.
 *
 * Passing these checks does not establish semantic correctness
 * or replace required human verification.
 */
public final class Grounding {

    private static final double CONFIDENCE_THRESHOLD = 0.75;

    private Grounding() {
    }

    /**
     * Fixed comparison rule:
     * 1. Collapse ASCII whitespace to one ordinary space.
     * 2. Remove leading and trailing ordinary spaces.
     * 3. Lowercase independently of the computer's default locale.
     *
     * Original source text and quotes remain unchanged.
     */
    public static String normalize(String text) {
        if (text == null) {
            throw new IllegalArgumentException(
                    "Text to normalise must not be null"
            );
        }

        return text
                .replaceAll("[ \\t\\n\\x0B\\f\\r]+", " ")
                .replaceAll("^ +| +$", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Checks for a literal quote match after normalisation.
     *
     * Retains the existing word-character boundary guards.
     * This is case-insensitive comparison, not paraphrase matching.
     */
    public static boolean quoteIsGrounded(
            String quote,
            String sourceText
    ) {
        if (quote == null || quote.isEmpty()
                || sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        String normalizedQuote = normalize(quote);
        String normalizedSource = normalize(sourceText);

        if (normalizedQuote.isEmpty()) {
            return false;
        }

        Pattern pattern = Pattern.compile(
                "(?<!\\w)"
                        + Pattern.quote(normalizedQuote)
                        + "(?!\\w)"
        );

        return pattern.matcher(normalizedSource).find();
    }

    public static final class GroundingFlags {

        public boolean sourceMissing = false;
        public boolean quoteNotGrounded = false;
        public boolean lowConfidence = false;
        public boolean tierViolation = false;
        public boolean contradiction = false;

        public boolean any() {
            return sourceMissing
                    || quoteNotGrounded
                    || lowConfidence
                    || tierViolation
                    || contradiction;
        }

        public Map<String, Boolean> asMap() {
            Map<String, Boolean> flags = new HashMap<>();

            flags.put("sourceMissing", sourceMissing);
            flags.put("quoteNotGrounded", quoteNotGrounded);
            flags.put("lowConfidence", lowConfidence);
            flags.put("tierViolation", tierViolation);
            flags.put("contradiction", contradiction);

            return flags;
        }
    }

    public record GroundingResult(
            String itemId,
            boolean isClean,
            GroundingFlags flags
    ) {
    }

    /**
     * Existing Map-based interface used by PipelineService.
     *
     * Expected keys:
     * itemId, sourceRef, quote, confidence, tier
     * and optional contradictsPriorTheme.
     *
     * sourceLookup maps source references to the de-identified
     * transcript text supplied to the coder.
     *
     * Callers must validate field types before calling this method.
     */
    public static GroundingResult ground(
            Map<String, Object> codedItem,
            Map<String, String> sourceLookup
    ) {
        if (codedItem == null) {
            throw new IllegalArgumentException(
                    "Coded item must not be null"
            );
        }

        if (sourceLookup == null) {
            throw new IllegalArgumentException(
                    "Source lookup must not be null"
            );
        }

        String itemId = codedItem.get("itemId") != null
                ? codedItem.get("itemId").toString()
                : "<unknown>";

        GroundingFlags flags = new GroundingFlags();

        Object sourceRefObject = codedItem.get("sourceRef");

        String sourceRef = sourceRefObject != null
                ? sourceRefObject.toString()
                : null;

        String sourceText = sourceRef != null
                ? sourceLookup.get(sourceRef)
                : null;

        if (sourceText == null) {
            flags.sourceMissing = true;
            return new GroundingResult(itemId, false, flags);
        }

        String quote = codedItem.get("quote") != null
                ? codedItem.get("quote").toString()
                : "";

        if (!quoteIsGrounded(quote, sourceText)) {
            flags.quoteNotGrounded = true;
        }

        double confidence = codedItem.get("confidence") != null
                ? ((Number) codedItem.get("confidence")).doubleValue()
                : 0.0;

        if (confidence < CONFIDENCE_THRESHOLD) {
            flags.lowConfidence = true;
        }

        Object tierObject = codedItem.get("tier");

        if (tierObject != null
                && ((Number) tierObject).intValue() == 3) {
            flags.tierViolation = true;
        }

        // Existing hook only, not automatic contradiction detection.
        Object contradicts = codedItem.get("contradictsPriorTheme");

        if (Boolean.TRUE.equals(contradicts)) {
            flags.contradiction = true;
        }

        return new GroundingResult(
                itemId,
                !flags.any(),
                flags
        );
    }

    /**
     * Connects the new structured finding to the existing checker.
     *
     * Recomputes checks instead of trusting previous finding flags.
     * This adapter does not perform contradiction detection.
     */
    public static GroundingResult groundFinding(
            CodedFinding finding,
            Map<String, String> sourceLookup
    ) {
        if (finding == null) {
            throw new IllegalArgumentException(
                    "Finding must not be null"
            );
        }

        if (sourceLookup == null) {
            throw new IllegalArgumentException(
                    "Source lookup must not be null"
            );
        }

        Map<String, Object> codedItem = new HashMap<>();

        codedItem.put("itemId", finding.itemId());
        codedItem.put("sourceRef", finding.sourceRef());
        codedItem.put("quote", finding.quote());
        codedItem.put("confidence", finding.confidence());
        codedItem.put("tier", finding.tier());

        return ground(codedItem, sourceLookup);
    }
}