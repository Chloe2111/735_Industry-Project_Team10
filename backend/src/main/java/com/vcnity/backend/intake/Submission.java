package com.vcnity.backend.intake;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One piece of feedback submitted through the community form.
 *
 * tierAtIntake is copied from the group when the submission arrives, so we always know
 * which tier it was processed under, even if the group is re-tiered later.
 */
@Document(collection = "intake_submissions")
public class Submission {

    @Id
    private String id;
    private List<String> groupIds = new ArrayList<>();
    /** The submitted text. Null for Tier 3 submissions: restricted text is never stored. */
    private String text;
    private Integer tierAtIntake;
    private SubmissionStatus status;
    /** Plain-language reason for the current status, when it is not simply "published". */
    private String statusReason;

    // Result of the automated coding step. Field names match the shared CodedFinding contract.
    private String theme;
    private String quote;
    private Double confidence;
    private List<String> flags = new ArrayList<>();

    /** Ids of the exceptions-queue items raised for this submission. */
    private List<String> exceptionIds = new ArrayList<>();
    private Instant submittedAt;
    private Instant updatedAt;

    public Submission() {
    }

    /** Independent copy, so a caller cannot change a stored record without saving it. */
    public Submission copy() {
        Submission c = new Submission();
        c.id = id;
        c.groupIds = new ArrayList<>(groupIds);
        c.text = text;
        c.tierAtIntake = tierAtIntake;
        c.status = status;
        c.statusReason = statusReason;
        c.theme = theme;
        c.quote = quote;
        c.confidence = confidence;
        c.flags = new ArrayList<>(flags);
        c.exceptionIds = new ArrayList<>(exceptionIds);
        c.submittedAt = submittedAt;
        c.updatedAt = updatedAt;
        return c;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getGroupIds() { return groupIds; }
    public void setGroupIds(List<String> groupIds) { this.groupIds = groupIds == null ? new ArrayList<>() : new ArrayList<>(groupIds); }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getTierAtIntake() { return tierAtIntake; }
    public void setTierAtIntake(Integer tierAtIntake) { this.tierAtIntake = tierAtIntake; }
    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public List<String> getFlags() { return flags; }
    public void setFlags(List<String> flags) { this.flags = flags == null ? new ArrayList<>() : new ArrayList<>(flags); }
    public List<String> getExceptionIds() { return exceptionIds; }
    public void setExceptionIds(List<String> exceptionIds) { this.exceptionIds = exceptionIds == null ? new ArrayList<>() : new ArrayList<>(exceptionIds); }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
