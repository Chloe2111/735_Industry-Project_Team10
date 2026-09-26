package com.vcnity.backend.security;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public final class CoverageCheck {
    
    private CoverageCheck() {}

    public static CoverageResult checkCoverage(Set<String> speakerCodes, List<CodedItem> outputItems) {
        // Fail fast on null inputs rather than letting a NullPointerException happen deeper in the method.
        if (speakerCodes == null || outputItems == null) {
            throw new IllegalArgumentException("speakerCodes and outputItems must not be null");
        }
        // No speakers means nothing can be missing — trivially complete.
        if (speakerCodes.isEmpty()) {
            return new CoverageResult(true, Set.of());
        }
        Set<String> coveredSpeakers = new HashSet<>();
        for (CodedItem item : outputItems) {
            if (item.speakerCode() == null) {
            continue;
            }
            coveredSpeakers.add(normalize(item.speakerCode()));
        }
        Set<String> missing = new HashSet<>();
            for (String speaker : speakerCodes) {
            if (!coveredSpeakers.contains(normalize(speaker))) {
                missing.add(speaker);
            }
            }
        return new CoverageResult(missing.isEmpty(), missing);
    }

// Standardises casing/whitespace so 'p1' and 'P1' are treated as the same speaker.    
    private static String normalize(String code) {
    return code.trim().toUpperCase();
}
}