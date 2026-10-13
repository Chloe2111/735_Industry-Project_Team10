package com.vcnity.backend.commission;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "commissions")
public class Commission {

    @Id
    private String id;

    private String title;
    private String description;
    private double incentive;
    private String deadline;

    // null == pending AI classification; no classifier is wired in yet.
    private String tier;

    private List<String> groups;
    private List<String> reportFormats;
    private String assignedGroup;
    private String status;
    private Instant createdAt;

    public Commission() {
    }

    public Commission(
            String title,
            String description,
            double incentive,
            String deadline,
            String tier,
            List<String> groups,
            List<String> reportFormats) {
        this.title = title;
        this.description = description;
        this.incentive = incentive;
        this.deadline = deadline;
        this.tier = tier;
        this.groups = groups;
        this.reportFormats = reportFormats;
        this.assignedGroup = null;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public double getIncentive() {
        return incentive;
    }

    public String getDeadline() {
        return deadline;
    }

    public String getTier() {
        return tier;
    }

    public List<String> getGroups() {
        return groups;
    }

    public List<String> getReportFormats() {
        return reportFormats;
    }

    public String getAssignedGroup() {
        return assignedGroup;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
