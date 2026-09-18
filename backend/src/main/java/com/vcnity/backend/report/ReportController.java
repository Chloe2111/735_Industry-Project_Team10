package com.vcnity.backend.report;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matches the response shape frontend/src/services/apiClient.js expects:
 * { success: true, data } on success, { success: false, message } on a 404.
 */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/reports")
    public Map<String, Object> list() {
        List<Report> reports = reportService.findAll();
        return Map.of("success", true, "data", reports);
    }

    @GetMapping("/api/reports/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return reportService
                .findDetailById(id)
                .<ResponseEntity<Map<String, Object>>>map(
                        report -> ResponseEntity.ok(Map.of("success", true, "data", report)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("success", false, "message", "Report not found.")));
    }
}
