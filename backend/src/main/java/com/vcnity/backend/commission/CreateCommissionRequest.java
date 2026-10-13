package com.vcnity.backend.commission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for POST /api/commissions.
 *
 * "tier" is optional: the client no longer picks it (VCNITY's AI is meant
 * to classify it), and there's no classifier wired in yet, so it's left
 * null ("pending classification") when omitted. When it IS present, it
 * still needs to be one of Tier 1/2/3 -- checked in CommissionService
 * rather than with an enum-backed constraint, so an invalid value comes
 * back as a field-level "tier" error the same way Bean Validation ones do.
 */
public class CreateCommissionRequest {

    @NotBlank(message = "Project title is required.")
    @Size(max = 200, message = "Project title must be 200 characters or fewer.")
    private String title;

    @Size(max = 4000, message = "Description must be 4000 characters or fewer.")
    private String description;

    @NotNull(message = "Incentive is required.")
    @Positive(message = "Incentive must be greater than zero.")
    private Double incentive;

    @NotBlank(message = "Response deadline is required.")
    private String deadline;

    private String tier;

    @NotEmpty(message = "Select at least one group.")
    private List<String> groups;

    private List<String> reportFormats;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getIncentive() {
        return incentive;
    }

    public void setIncentive(Double incentive) {
        this.incentive = incentive;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getReportFormats() {
        return reportFormats;
    }

    public void setReportFormats(List<String> reportFormats) {
        this.reportFormats = reportFormats;
    }
}
