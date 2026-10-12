package com.vcnity.backend.intake;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Group-level tiering rules.
 *
 * - A group has no tier until a person sets one. There is no default.
 * - Every change records who made it, when and why, and is added to the group's history.
 * - A submission to several groups takes the most restrictive (highest) tier.
 *
 * Nothing in the pipeline calls {@link #setTier}; it is reached only from the tier endpoint.
 */
@Service
public class GroupTierService {

    static final int MAX_GROUP_ID_LENGTH = 100;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_REASON_LENGTH = 500;

    private final GroupTierStore store;

    public GroupTierService(GroupTierStore store) {
        this.store = store;
    }

    /** The result of looking up the tier for one or more groups. */
    public record TierResolution(Integer tier, List<String> unclassifiedGroupIds) {

        public boolean isClassified() {
            return unclassifiedGroupIds.isEmpty();
        }
    }

    public List<GroupTier> list() {
        return store.findAll();
    }

    public Optional<GroupTier> get(String groupId) {
        return store.findById(normaliseGroupId(groupId));
    }

    /**
     * Group ids are matched without regard to case or extra spaces,
     * so "Youth Group A" and " youth  group a " are the same group.
     */
    public static String normaliseGroupId(String groupId) {
        if (groupId == null) return "";
        return groupId.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Works out the tier a submission must be handled under.
     * If any group is unclassified the result has no tier, and intake must refuse the submission.
     */
    public TierResolution resolve(List<String> groupIds) {
        Set<String> unclassified = new LinkedHashSet<>();
        Integer mostRestrictive = null;
        for (String groupId : groupIds) {
            Optional<GroupTier> record = get(groupId);
            if (record.isEmpty() || !Tiers.isValid(record.get().getTier())) {
                unclassified.add(normaliseGroupId(groupId));
                continue;
            }
            int tier = record.get().getTier();
            if (mostRestrictive == null || tier > mostRestrictive) {
                mostRestrictive = tier;
            }
        }
        if (!unclassified.isEmpty() || mostRestrictive == null) {
            return new TierResolution(null, new ArrayList<>(unclassified));
        }
        return new TierResolution(mostRestrictive, List.of());
    }

    /**
     * Sets or changes a group's tier. Package-private on purpose: application code goes through
     * {@link IntakeService#classifyGroup}, which also halts queued submissions when needed.
     */
    GroupTier setTier(String groupId, String groupName, Integer tier, String setBy, String reason) {
        String id = normaliseGroupId(groupId);
        if (id.isEmpty()) throw new IllegalArgumentException("A group is required.");
        if (id.length() > MAX_GROUP_ID_LENGTH) {
            throw new IllegalArgumentException("Group must be " + MAX_GROUP_ID_LENGTH + " characters or fewer.");
        }
        if (!Tiers.isValid(tier)) throw new IllegalArgumentException("Tier must be 1, 2 or 3.");
        String who = setBy == null ? "" : setBy.trim();
        if (who.isEmpty()) throw new IllegalArgumentException("Your name is required: only a person can set a tier.");
        if (who.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name must be " + MAX_NAME_LENGTH + " characters or fewer.");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new IllegalArgumentException("A short reason is required.");
        if (why.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }

        GroupTier record = store.findById(id).orElseGet(() -> {
            GroupTier created = new GroupTier();
            created.setGroupId(id);
            return created;
        });
        String displayName = groupName == null ? "" : groupName.trim().replaceAll("\\s+", " ");
        if (!displayName.isEmpty()) {
            if (displayName.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Group name must be " + MAX_NAME_LENGTH + " characters or fewer.");
            }
            record.setGroupName(displayName);
        } else if (record.getGroupName() == null) {
            record.setGroupName(groupId.trim().replaceAll("\\s+", " "));
        }

        Instant now = Instant.now();
        record.getHistory().add(new GroupTier.TierChange(record.getTier(), tier, who, why, now));
        record.setTier(tier);
        record.setSetBy(who);
        record.setReason(why);
        record.setSetAt(now);
        return store.save(record);
    }
}
