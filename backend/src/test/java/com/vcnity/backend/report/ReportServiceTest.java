package com.vcnity.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository repository;

    private Map<String, Object> validSource(String id) {
        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
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

    private Map<String, Object> finding(String id, List<Map<String, Object>> sources) {
        Map<String, Object> finding = new HashMap<>();
        finding.put("id", id);
        finding.put("title", "A theme");
        finding.put("commissioningBody", "Council");
        finding.put("deliveredBy", "Group");
        finding.put("researchQuestion", "Question?");
        finding.put("method", "Method.");
        finding.put("analysis", "Analysis.");
        finding.put("deliverableStatus", "Complete");
        finding.put("sourceCount", 99); // deliberately wrong, should be reconciled
        finding.put("sources", new ArrayList<>(sources));
        return finding;
    }

    private Report validReport() {
        Report report = new Report();
        report.setId("r1");
        report.setTitle("A report");
        report.setSubtitle("Subtitle");
        report.setPreparedFor("[Council 1]");
        report.setDate("Today");
        report.setFile("file-1");
        report.setPurpose("Purpose.");
        report.setFindingsIntro("Intro.");
        report.setBadges(List.of("AI-assisted"));
        report.setStats(List.of(Map.of("label", "Participants", "value", "47")));
        return report;
    }

    @Test
    void findingsWithoutLinkedEvidenceAreWithheld() {
        Report report = validReport();
        report.setFindings(List.of(
                finding("finding-1", List.of(validSource("SRV-001"))),
                finding("finding-2", List.of()) // no linked evidence
        ));
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        ReportService service = new ReportService(repository);
        Optional<Report> result = service.findDetailById("r1");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getFindings().size());
        assertEquals("finding-1", result.get().getFindings().get(0).get("id"));
        assertEquals(1, result.get().getWithheldFindingsCount());
    }

    @Test
    void malformedSourcesAreDroppedAndSourceCountReconciled() {
        Map<String, Object> badSource = validSource("SRV-BAD");
        badSource.put("quote", "");

        Report report = validReport();
        report.setFindings(List.of(finding("finding-1", List.of(validSource("SRV-001"), badSource))));
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        ReportService service = new ReportService(repository);
        Map<String, Object> resultFinding = service.findDetailById("r1").get().getFindings().get(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) resultFinding.get("sources");
        assertEquals(1, sources.size());
        assertEquals("SRV-001", sources.get(0).get("id"));
        assertEquals(1, resultFinding.get("sourceCount"));
    }

    @Test
    void reportFailingValidationIsTreatedAsNotFound() {
        Report report = validReport();
        report.setPurpose(""); // blank required field
        report.setFindings(List.of());
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        ReportService service = new ReportService(repository);

        assertFalse(service.findDetailById("r1").isPresent());
    }

    @Test
    void unknownIdIsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        ReportService service = new ReportService(repository);

        assertFalse(service.findDetailById("missing").isPresent());
    }
}
