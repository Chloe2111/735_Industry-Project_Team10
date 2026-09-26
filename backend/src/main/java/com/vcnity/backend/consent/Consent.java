package com.vcnity.backend.consent;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "consents")
public class Consent {

    @Id
    private String id;

    private String participantName;
    private String groupName;
    private int tier;
    private boolean consentGiven;
    private String notes;
    private Instant submittedAt;

    public Consent() {
    }

    public Consent(String participantName, String groupName, int tier, boolean consentGiven, String notes) {
        this.participantName = participantName;
        this.groupName = groupName;
        this.tier = tier;
        this.consentGiven = consentGiven;
        this.notes = notes;
        this.submittedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
