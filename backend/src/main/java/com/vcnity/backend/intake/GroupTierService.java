package com.vcnity.backend.intake;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Group-level tiering rules.
 *
 * - A group has no tier until a person sets one. There is no default.
 * - Every change records who made it, when and why, and is added to the group's history.
 * - A change is refused if the group was changed by someone else in the meantime.
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
     * Works out the tier a submission must be handled under, with one database query
     * however many groups it names. If any group is unclassified the result has no tier,
     * and intake must refuse the submission.
     */
    public TierResolution resolve(List<String> groupIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String groupId : groupIds) {
            ids.add(normaliseGroupId(groupId));
        }
        Map<String, GroupTier> records = store.findByIds(ids).stream()
                .collect(Collectors.toMap(GroupTier::getGroupId, Function.identity(), (a, b) -> a));

        List<String> unclassified = new ArrayList<>();
        Integer mostRestrictive = null;
        for (String id : ids) {
            GroupTier record = records.get(id);
            if (record == null || !Tiers.isValid(record.getTier())) {
                unclassified.add(id);
                continue;
            }
            if (mostRestrictive == null || record.getTier() > mostRestrictive) {
                mostRestrictive = record.getTier();
            }
        }
        if (!unclassified.isEmpty() || mostRestrictive == null) {
            return new TierResolution(null, unclassified);
        }
        return new TierResolution(mostRestrictive, List.of());
    }

    /**
     * Which of these groups are Tier 3 right now. One database query however many groups are asked about.
     * Used to keep a group's posts hidden while it is Tier 3, whatever tier they were submitted under.
     */
    public Set<String> restrictedAmong(Collection<String> groupIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String groupId : groupIds) {
            ids.add(normaliseGroupId(groupId));
        }
        if (ids.isEmpty()) return Set.of();
        return store.findByIds(ids).stream()
                .filter(record -> record.getTier() != null && record.getTier() == Tiers.RESTRICTED)
                .map(GroupTier::getGroupId)
                .collect(Collectors.toSet());
    }

    /**
     * Sets or changes a group's tier. Package-private on purpose: application code goes through
     * {@link IntakeService#classifyGroup}, which also halts the group's submissions when it becomes Tier 3.
     *
     * @param expectedVersion the version of the record the person was looking at, or null if they
     *                        believe the group is not classified yet
     * @throws ConcurrentTierChangeException if the group was changed by someone else since then
     */
    GroupTier setTier(String groupId, String groupName, Integer tier, String setBy, String reason,
                      Long expectedVersion) {
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
        String displayName = groupName == null ? "" : groupName.trim().replaceAll("\\s+", " ");
        if (displayName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Group name must be " + MAX_NAME_LENGTH + " characters or fewer.");
        }

        Optional<GroupTier> existing = store.findById(id);
        // The person decided while looking at one version of the record. If it is no longer that
        // version, their decision was made on stale information, so it must not overwrite the newer one.
        Long storedVersion = existing.map(GroupTier::getVersion).orElse(null);
        if (!Objects.equals(storedVersion, expectedVersion)) {
            throw new ConcurrentTierChangeException(staleMessage(existing));
        }

        GroupTier record = existing.orElseGet(() -> {
            GroupTier created = new GroupTier();
            created.setGroupId(id);
            return created;
        });
        if (!displayName.isEmpty()) {
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
        // The store checks the version once more at the moment of writing, which closes the gap
        // between the read above and this save.
        return store.save(record);
    }

    private static String staleMessage(Optional<GroupTier> current) {
        if (current.isEmpty()) {
            return "This group has no tier on record any more. Reload the page and try again.";
        }
        GroupTier record = current.get();
        String name = record.getGroupName() == null ? record.getGroupId() : record.getGroupName();
        return name + " was set to Tier " + record.getTier() + " by " + record.getSetBy()
                + " while you were editing. Your change was not saved. Reload the page and check before changing it again.";
    }
}
