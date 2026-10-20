package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps group tiers in memory. Used by the tests, and by the app when started with
 * {@code intake.store=memory} (no MongoDB needed; everything is lost on restart).
 * Stores and returns copies and checks the version on save, like the real database does.
 */
@Repository
@ConditionalOnProperty(name = "intake.store", havingValue = "memory")
public class InMemoryGroupTierStore implements GroupTierStore {

    private final Map<String, GroupTier> groups = new ConcurrentHashMap<>();

    @Override
    public GroupTier save(GroupTier groupTier) {
        long[] savedVersion = new long[1];
        // compute() runs once per key at a time, so the version check and the write cannot be interleaved.
        groups.compute(groupTier.getGroupId(), (id, stored) -> {
            Long storedVersion = stored == null ? null : stored.getVersion();
            if (!Objects.equals(storedVersion, groupTier.getVersion())) {
                throw new ConcurrentTierChangeException(
                        "This group was changed by someone else at the same moment. Reload and try again.");
            }
            GroupTier next = groupTier.copy();
            savedVersion[0] = storedVersion == null ? 0L : storedVersion + 1;
            next.setVersion(savedVersion[0]);
            return next;
        });
        groupTier.setVersion(savedVersion[0]);
        return groupTier;
    }

    @Override
    public Optional<GroupTier> findById(String groupId) {
        GroupTier found = groupId == null ? null : groups.get(groupId);
        return found == null ? Optional.empty() : Optional.of(found.copy());
    }

    @Override
    public List<GroupTier> findByIds(Collection<String> groupIds) {
        return groupIds.stream()
                .distinct()
                .map(groups::get)
                .filter(Objects::nonNull)
                .map(GroupTier::copy)
                .toList();
    }

    @Override
    public List<GroupTier> findAll() {
        return groups.values().stream()
                .sorted(Comparator.comparing(GroupTier::getGroupId))
                .map(GroupTier::copy)
                .toList();
    }
}
