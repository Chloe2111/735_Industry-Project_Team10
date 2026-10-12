package com.vcnity.backend.intake;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The tier classification of one community group.
 *
 * The tier belongs to the group, not to the author of a post. There is no default:
 * a group with no record here (or a null tier) is "not yet classified" and intake refuses its posts.
 * Only a person sets the tier; setBy, reason and setAt record who, why and when.
 */
@Document(collection = "group_tiers")
public class GroupTier {

    @Id
    private String groupId;
    private String groupName;
    private Integer tier;
    private String setBy;
    private String reason;
    private Instant setAt;
    private List<TierChange> history = new ArrayList<>();

    public GroupTier() {
    }

    /** One entry in a group's tier history. fromTier is null for the first classification. */
    public static class TierChange {
        private Integer fromTier;
        private Integer toTier;
        private String setBy;
        private String reason;
        private Instant at;

        public TierChange() {
        }

        public TierChange(Integer fromTier, Integer toTier, String setBy, String reason, Instant at) {
            this.fromTier = fromTier;
            this.toTier = toTier;
            this.setBy = setBy;
            this.reason = reason;
            this.at = at;
        }

        public Integer getFromTier() { return fromTier; }
        public void setFromTier(Integer fromTier) { this.fromTier = fromTier; }
        public Integer getToTier() { return toTier; }
        public void setToTier(Integer toTier) { this.toTier = toTier; }
        public String getSetBy() { return setBy; }
        public void setSetBy(String setBy) { this.setBy = setBy; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Instant getAt() { return at; }
        public void setAt(Instant at) { this.at = at; }
    }

    /** Independent copy, so a caller cannot change a stored record without saving it. */
    public GroupTier copy() {
        GroupTier c = new GroupTier();
        c.groupId = groupId;
        c.groupName = groupName;
        c.tier = tier;
        c.setBy = setBy;
        c.reason = reason;
        c.setAt = setAt;
        for (TierChange h : history) {
            c.history.add(new TierChange(h.fromTier, h.toTier, h.setBy, h.reason, h.at));
        }
        return c;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }
    public String getSetBy() { return setBy; }
    public void setSetBy(String setBy) { this.setBy = setBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getSetAt() { return setAt; }
    public void setSetAt(Instant setAt) { this.setAt = setAt; }
    public List<TierChange> getHistory() { return history; }
    public void setHistory(List<TierChange> history) { this.history = history == null ? new ArrayList<>() : new ArrayList<>(history); }
}
