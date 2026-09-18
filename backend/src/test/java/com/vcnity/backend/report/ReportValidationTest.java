package com.vcnity.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportValidationTest {

    private Map<String, Object> validSource() {
        Map<String, Object> source = new HashMap<>();
        source.put("id", "SRV-001");
        source.put("type", "Survey response");
        source.put("method", "Online survey");
        source.put("quote", "A real quote.");
        source.put("agreementPercent", 70);
        source.put("participantsAgreed", 20);
        source.put("participantsTotal", 30);
        source.put("ageRange", "15-20 years old");
        source.put("genderBreakdown", List.of(Map.of("label", "male", "percent", 100)));
        source.put("culturalBackgrounds", List.of("Anglo-Australian"));
        source.put("whyItMatters", "It matters.");
        source.put("themes", List.of("Access"));
        return source;
    }

    private Map<String, Object> validFinding() {
        Map<String, Object> finding = new HashMap<>();
        finding.put("id", "finding-1");
        finding.put("title", "A theme");
        finding.put("commissioningBody", "Council");
        finding.put("deliveredBy", "Group");
        finding.put("researchQuestion", "Question?");
        finding.put("method", "Method.");
        finding.put("analysis", "Analysis.");
        finding.put("deliverableStatus", "Complete");
        finding.put("sources", new java.util.ArrayList<>(List.of(validSource())));
        return finding;
    }

    @Test
    void validSourcePasses() {
        assertTrue(ReportValidation.isValidSource(validSource()));
    }

    @Test
    void sourceWithBlankQuoteFails() {
        Map<String, Object> source = validSource();
        source.put("quote", "   ");
        assertFalse(ReportValidation.isValidSource(source));
    }

    @Test
    void sourceWithMissingFieldFails() {
        Map<String, Object> source = validSource();
        source.remove("whyItMatters");
        assertFalse(ReportValidation.isValidSource(source));
    }

    @Test
    void sourceWithOutOfRangeAgreementPercentFails() {
        Map<String, Object> source = validSource();
        source.put("agreementPercent", 150);
        assertFalse(ReportValidation.isValidSource(source));
    }

    @Test
    void sourceWithAgreedGreaterThanTotalFails() {
        Map<String, Object> source = validSource();
        source.put("participantsAgreed", 99);
        source.put("participantsTotal", 30);
        assertFalse(ReportValidation.isValidSource(source));
    }

    @Test
    void sourceWithEmptyThemesFails() {
        Map<String, Object> source = validSource();
        source.put("themes", List.of());
        assertFalse(ReportValidation.isValidSource(source));
    }

    @Test
    void findingWithAtLeastOneValidSourcePasses() {
        assertTrue(ReportValidation.isValidFinding(validFinding()));
    }

    @Test
    void findingWithNoSourcesFails() {
        Map<String, Object> finding = validFinding();
        finding.put("sources", List.of());
        assertFalse(ReportValidation.isValidFinding(finding));
    }

    @Test
    void findingWithOnlyMalformedSourcesFails() {
        Map<String, Object> finding = validFinding();
        Map<String, Object> badSource = validSource();
        badSource.put("quote", "");
        finding.put("sources", List.of(badSource));
        assertFalse(ReportValidation.isValidFinding(finding));
    }

    @Test
    void findingWithMixOfValidAndMalformedSourcesStillPasses() {
        Map<String, Object> finding = validFinding();
        Map<String, Object> badSource = validSource();
        badSource.put("id", "SRV-002");
        badSource.put("quote", "");
        finding.put("sources", List.of(validSource(), badSource));

        assertTrue(ReportValidation.isValidFinding(finding));
        assertEquals(1, ReportValidation.validSourcesIn(finding).toList().size());
    }

    @Test
    void findingWithBlankRequiredTextFieldFails() {
        Map<String, Object> finding = validFinding();
        finding.put("analysis", "");
        assertFalse(ReportValidation.isValidFinding(finding));
    }
}
