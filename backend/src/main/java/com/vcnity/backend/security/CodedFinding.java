package com.vcnity.backend.security;

import java.util.List;
import java.util.Set;

/**
 * One structured coding result.
 *
 * Empty flags mean no failures have been recorded.
 * They do not prove grounding has run or grant final approval.
 */
public record CodedFinding(
        String itemId,
        String sourceRef,
        String speakerCode,
        String theme,
        String quote,
        Double confidence,
        Integer tier,
        List<String> flags
) {

    private static final Set<String> SUPPORTED_FLAGS = Set.of(
            "quoteNotGrounded",
            "lowConfidence",
            "sourceMissing",
            "contradiction",
            "tierViolation"
    );

    public CodedFinding {
        requireText(itemId, "itemId");
        requireText(sourceRef, "sourceRef");
        requireText(theme, "theme");
        requireText(quote, "quote");

        if (speakerCode != null && speakerCode.isBlank()) {
            throw new IllegalArgumentException(
                    "speakerCode must be null or non-blank"
            );
        }

        if (confidence == null
                || !Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
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
                checkedFlags
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