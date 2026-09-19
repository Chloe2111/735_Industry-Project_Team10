package com.vcnity.backend.exceptions.model;

import java.time.LocalDateTime;

public class ExceptionItem {
    private String id;
    private FlagType flagType;
    private String sourceQuote;
    private String sourceContext;
    private Double confidence;
    private String sourceRef;
    private ReviewStatus status;
    private String reviewerNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public ExceptionItem() {}

    public ExceptionItem(String id, FlagType flagType, String sourceQuote, String sourceContext,
                         Double confidence, String sourceRef) {
        this.id = id;
        this.flagType = flagType;
        this.sourceQuote = sourceQuote;
        this.sourceContext = sourceContext;
        this.confidence = confidence;
        this.sourceRef = sourceRef;
        this.status = ReviewStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public FlagType getFlagType() { return flagType; }
    public void setFlagType(FlagType flagType) { this.flagType = flagType; }
    public String getSourceQuote() { return sourceQuote; }
    public void setSourceQuote(String sourceQuote) { this.sourceQuote = sourceQuote; }
    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
