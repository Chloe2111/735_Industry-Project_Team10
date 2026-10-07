package com.vcnity.backend.intake;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Where group tier classifications are kept. MongoDB in the running app; in-memory for tests and demos. */
public interface GroupTierStore {

    /**
     * Saves the record only if nobody else has saved it since it was read.
     *
     * @throws ConcurrentTierChangeException if the stored version is not the one this record carries
     */
    GroupTier save(GroupTier groupTier);

    Optional<GroupTier> findById(String groupId);

    /** The records for these group ids, in one query. Ids with no record are simply missing from the result. */
    List<GroupTier> findByIds(Collection<String> groupIds);

    /** Every classified group, ordered by groupId. */
    List<GroupTier> findAll();
}
