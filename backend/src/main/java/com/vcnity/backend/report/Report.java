package com.vcnity.backend.report;

import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A commissioned-report record, covering both the Reports Received list
 * view (project/group/reportingPeriod/status) and the Report Detail view
 * (everything else).
 *
 * "stats" and "findings" (findings carry their own nested "sources") stay
 * as flexible Map/List structures rather than a dozen dedicated POJOs --
 * this data is read-only and deeply nested (source -> gender breakdown,
 * cultural backgrounds, themes), matching how PipelineService.java already
 * uses Map<String, Object> for this kind of shape.
 *
 * A report with no "title" has been listed (e.g. status DRAFT) but has no
 * detail content yet -- ReportService.findById treats that as not found,
 * same as the old mock's reportDetails lookup did.
 */
@Document(collection = "reports")
public class Report {

    @Id
    private String id;

    private String project;
    private String group;
    private String reportingPeriod;
    private String status;

    private String title;
    private String subtitle;
    private List<String> badges;
    private String preparedFor;
    private String date;
    private String file;
    private String purpose;
    private List<Map<String, Object>> stats;
    private String findingsIntro;
    private List<Map<String, Object>> findings;

    /**
     * Computed at response time by ReportService, not stored -- how many
     * themes were withheld from "findings" for failing display-data
     * validation (no field empty/malformed, at least one linked quote).
     */
    @Transient
    private int withheldFindingsCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(String reportingPeriod) {
        this.reportingPeriod = reportingPeriod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public List<String> getBadges() {
        return badges;
    }

    public void setBadges(List<String> badges) {
        this.badges = badges;
    }

    public String getPreparedFor() {
        return preparedFor;
    }

    public void setPreparedFor(String preparedFor) {
        this.preparedFor = preparedFor;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public List<Map<String, Object>> getStats() {
        return stats;
    }

    public void setStats(List<Map<String, Object>> stats) {
        this.stats = stats;
    }

    public String getFindingsIntro() {
        return findingsIntro;
    }

    public void setFindingsIntro(String findingsIntro) {
        this.findingsIntro = findingsIntro;
    }

    public List<Map<String, Object>> getFindings() {
        return findings;
    }

    public void setFindings(List<Map<String, Object>> findings) {
        this.findings = findings;
    }

    public int getWithheldFindingsCount() {
        return withheldFindingsCount;
    }

    public void setWithheldFindingsCount(int withheldFindingsCount) {
        this.withheldFindingsCount = withheldFindingsCount;
    }
}
