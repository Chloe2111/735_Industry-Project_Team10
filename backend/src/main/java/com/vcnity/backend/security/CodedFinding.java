package com.vcnity.backend.security;

import java.util.List;
import java.util.Set;

/**
 * One structured coding result.
 *
 * Empty flags mean no failures have been recorded.
 * They do not prove grounding has run or grant final approval.
 *
 * codedBy says who chose the theme and quote: "ai" (the pipeline) or "facilitator"
 * (a person coded an idea the AI could not code). Only a facilitator-coded finding may
 * have no confidence, because there is no AI score for it.
 */
public record CodedFinding(
        String itemId,
        String sourceRef,
        String speakerCode,
        String theme,
        String quote,
        Double confidence,
        Integer tier,
        List<String> flags,
        String codedBy
) {

    public static final String CODED_BY_AI = "ai";
    public static final String CODED_BY_FACILITATOR = "facilitator";

    private static final Set<String> SUPPORTED_FLAGS = Set.of(
            "quoteNotGrounded",
            "lowConfidence",
            "sourceMissing",
            "contradiction",
            "tierViolation"
    );

    /** An AI-coded finding. Every existing caller uses this, so nothing else had to change. */
    public CodedFinding(String itemId, String sourceRef, String speakerCode, String theme, String quote,
                        Double confidence, Integer tier, List<String> flags) {
        this(itemId, sourceRef, speakerCode, theme, quote, confidence, tier, flags, CODED_BY_AI);
    }

    /** A finding a facilitator coded by hand: no speaker code, no AI confidence, no flags. */
    public static CodedFinding codedByFacilitator(String itemId, String sourceRef, String theme,
                                                  String quote, Integer tier) {
        return new CodedFinding(itemId, sourceRef, null, theme, quote, null, tier, List.of(),
                CODED_BY_FACILITATOR);
    }

    public CodedFinding {
        // Records saved before codedBy existed were all coded by the AI.
        if (codedBy == null) {
            codedBy = CODED_BY_AI;
        }
        if (!CODED_BY_AI.equals(codedBy) && !CODED_BY_FACILITATOR.equals(codedBy)) {
            throw new IllegalArgumentException(
                    "codedBy must be \"ai\" or \"facilitator\""
            );
        }

        requireText(itemId, "itemId");
        requireText(sourceRef, "sourceRef");
        requireText(theme, "theme");
        requireText(quote, "quote");

        if (speakerCode != null && speakerCode.isBlank()) {
            throw new IllegalArgumentException(
                    "speakerCode must be null or non-blank"
            );
        }

        boolean noScoreAllowed = CODED_BY_FACILITATOR.equals(codedBy) && confidence == null;
        if (!noScoreAllowed && (confidence == null
                || !Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0)) {
            throw new IllegalArgumentException(
                    "confidence must be a finite number between 0 and 1"
            );
        }

        if (tier == null || (tier != 1 && tier != 2)) {
            throw new IllegalArgumentException(
                    "Coding requires tier 1 or tier 2"
            );
        }

        if (flags == null) {
            throw new IllegalArgumentException(
                    "flags must not be null"
            );
        }

        for (String flag : flags) {
            if (flag == null || !SUPPORTED_FLAGS.contains(flag)) {
                throw new IllegalArgumentException(
                        "Unsupported grounding flag"
                );
            }
        }

        if (flags.stream().distinct().count() != flags.size()) {
            throw new IllegalArgumentException(
                    "flags must not contain duplicates"
            );
        }

        flags = List.copyOf(flags);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
    }

    /**
     * Creates a new finding containing the completed grounding flags.
     */
    public CodedFinding withFlags(List<String> checkedFlags) {
        return new CodedFinding(
                itemId,
                sourceRef,
                speakerCode,
                theme,
                quote,
                confidence,
                tier,
                checkedFlags,
                codedBy
        );
    }

    /**
     * Adapts this finding to the existing coverage-check interface.
     * Missing speaker information remains missing.
     */
    public CodedItem toCoverageItem() {
        return new CodedItem(itemId, speakerCode);
    }
}