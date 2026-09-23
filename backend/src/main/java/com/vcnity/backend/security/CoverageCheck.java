package com.vcnity.backend.security;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public final class CoverageCheck {
    private CoverageCheck() {}

    public static CoverageResult checkCoverage(Set<String> speakerCodes, List<CodedItem> outputItems) {
        Set<String> coveredSpeakers = new HashSet<>();
        for (CodedItem item : outputItems) {
            coveredSpeakers.add(item.speakerCode());
        }
        Set<String> missing = new HashSet<>(speakerCodes);
        missing.removeAll(coveredSpeakers);
        return new CoverageResult(missing.isEmpty(), missing);
    }
}