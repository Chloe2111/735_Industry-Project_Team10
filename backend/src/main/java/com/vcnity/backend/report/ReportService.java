package com.vcnity.backend.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public List<Report> findAll() {
        return repository.findAll();
    }

    /**
     * A report with no detail content yet (e.g. still DRAFT) or that fails
     * display-data validation is treated as not found. A report that does
     * pass has its findings filtered down to only the ones with valid,
     * linked evidence -- withheldFindingsCount records how many were cut.
     */
    public Optional<Report> findDetailById(String id) {
        return repository.findById(id)
                .filter(ReportValidation::isValidReport)
                .map(this::withValidatedFindings);
    }

    private Report withValidatedFindings(Report report) {
        List<Map<String, Object>> all = report.getFindings() == null ? List.of() : report.getFindings();

        List<Map<String, Object>> valid = all.stream()
                .filter(ReportValidation::isValidFinding)
                .map(this::withValidatedSources)
                .toList();

        report.setFindings(valid);
        report.setWithheldFindingsCount(all.size() - valid.size());
        return report;
    }

    /** Drops any individually malformed source, and reconciles sourceCount to what's actually shown. */
    private Map<String, Object> withValidatedSources(Map<String, Object> finding) {
        List<Map<String, Object>> validSources = ReportValidation.validSourcesIn(finding).toList();

        Map<String, Object> sanitized = new java.util.LinkedHashMap<>(finding);
        sanitized.put("sources", validSources);
        sanitized.put("sourceCount", validSources.size());
        return sanitized;
    }
}
