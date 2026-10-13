package com.vcnity.backend.consent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ConsentRequest {

    private String participantName;

    @NotBlank(message = "groupName is required")
    private String groupName;

    @Min(value = 1, message = "tier must be 1, 2, or 3")
    @Max(value = 3, message = "tier must be 1, 2, or 3")
    private int tier;

    private boolean consentGiven;
    private String notes;

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
}
