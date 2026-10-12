package com.vcnity.backend.intake;

import java.util.List;
import java.util.Optional;

/** Where group tier classifications are kept. MongoDB in the running app; in-memory for tests and demos. */
public interface GroupTierStore {

    GroupTier save(GroupTier groupTier);

    Optional<GroupTier> findById(String groupId);

    /** Every classified group, ordered by groupId. */
    List<GroupTier> findAll();
}
