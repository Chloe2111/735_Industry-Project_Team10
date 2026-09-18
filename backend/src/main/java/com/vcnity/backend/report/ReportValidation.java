package com.vcnity.backend.report;

import java.util.List;
import java.util.Map;

/**
 * Display-data validation for the reporting view: a theme (finding) is only
 * shown once it has at least one well-formed linked evidence quote, and no
 * individual field is empty or malformed. Filtering happens here, backend
 * side, before the API responds -- the frontend only ever receives findings
 * and sources that have already passed these checks.
 */
public final class ReportValidation {

    private ReportValidation() {
    }

    public static boolean isValidReport(Report report) {
        return isNonBlank(report.getTitle())
                && isNonBlank(report.getSubtitle())
                && isNonBlank(report.getPreparedFor())
                && isNonBlank(report.getDate())
                && isNonBlank(report.getFile())
                && isNonBlank(report.getPurpose())
                && isNonBlank(report.getFindingsIntro())
                && report.getBadges() != null && !report.getBadges().isEmpty()
                && report.getStats() != null && !report.getStats().isEmpty()
                && report.getStats().stream().allMatch(ReportValidation::isValidStat);
    }

    /** A theme is valid only once it has its own required text and at least one linked evidence quote. */
    public static boolean isValidFinding(Map<String, Object> finding) {
        if (finding == null
                || !isNonBlank(finding.get("id"))
                || !isNonBlank(finding.get("title"))
                || !isNonBlank(finding.get("commissioningBody"))
                || !isNonBlank(finding.get("deliveredBy"))
                || !isNonBlank(finding.get("researchQuestion"))
                || !isNonBlank(finding.get("method"))
                || !isNonBlank(finding.get("analysis"))
                || !isNonBlank(finding.get("deliverableStatus"))) {
            return false;
        }
        return validSourcesIn(finding).findAny().isPresent();
    }

    public static boolean isValidSource(Object candidate) {
        if (!(candidate instanceof Map<?, ?> raw)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) raw;

        Integer agreementPercent = asInt(source.get("agreementPercent"));
        Integer participantsAgreed = asInt(source.get("participantsAgreed"));
        Integer participantsTotal = asInt(source.get("participantsTotal"));

        return isNonBlank(source.get("id"))
                && isNonBlank(source.get("type"))
                && isNonBlank(source.get("method"))
                && isNonBlank(source.get("quote"))
                && isNonBlank(source.get("ageRange"))
                && isNonBlank(source.get("whyItMatters"))
                && isNonEmptyList(source.get("genderBreakdown"))
                && isNonEmptyList(source.get("culturalBackgrounds"))
                && isNonEmptyList(source.get("themes"))
                && agreementPercent != null && agreementPercent >= 0 && agreementPercent <= 100
                && participantsTotal != null && participantsTotal > 0
                && participantsAgreed != null && participantsAgreed >= 0 && participantsAgreed <= participantsTotal;
    }

    @SuppressWarnings("unchecked")
    static java.util.stream.Stream<Map<String, Object>> validSourcesIn(Map<String, Object> finding) {
        Object sources = finding.get("sources");
        if (!(sources instanceof List<?> list)) {
            return java.util.stream.Stream.empty();
        }
        return list.stream()
                .filter(ReportValidation::isValidSource)
                .map(s -> (Map<String, Object>) s);
    }

    private static boolean isValidStat(Map<String, Object> stat) {
        return stat != null && isNonBlank(stat.get("label")) && isNonBlank(stat.get("value"));
    }

    private static boolean isNonBlank(Object value) {
        return value instanceof String s && !s.isBlank();
    }

    private static boolean isNonEmptyList(Object value) {
        return value instanceof List<?> list && !list.isEmpty();
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
