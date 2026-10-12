package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps group tiers in memory. Used by the tests, and by the app when started with
 * {@code intake.store=memory} (no MongoDB needed; everything is lost on restart).
 * Stores and returns copies, like a real database would.
 */
@Repository
@ConditionalOnProperty(name = "intake.store", havingValue = "memory")
public class InMemoryGroupTierStore implements GroupTierStore {

    private final Map<String, GroupTier> groups = new ConcurrentHashMap<>();

    @Override
    public GroupTier save(GroupTier groupTier) {
        groups.put(groupTier.getGroupId(), groupTier.copy());
        return groupTier;
    }

    @Override
    public Optional<GroupTier> findById(String groupId) {
        GroupTier found = groupId == null ? null : groups.get(groupId);
        return found == null ? Optional.empty() : Optional.of(found.copy());
    }

    @Override
    public List<GroupTier> findAll() {
        return groups.values().stream()
                .sorted(Comparator.comparing(GroupTier::getGroupId))
                .map(GroupTier::copy)
                .toList();
    }
}
