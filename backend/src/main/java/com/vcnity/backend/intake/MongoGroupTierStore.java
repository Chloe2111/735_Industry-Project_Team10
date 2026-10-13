package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Keeps group tiers in MongoDB (collection "group_tiers"). This is the default store. */
@Repository
@ConditionalOnProperty(name = "intake.store", havingValue = "mongo", matchIfMissing = true)
public class MongoGroupTierStore implements GroupTierStore {

    private final MongoTemplate mongo;

    public MongoGroupTierStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * GroupTier has an @Version field, so Spring Data only writes when the stored version still
     * matches. A lost race shows up as one of two exceptions, both meaning "someone else got there first":
     * OptimisticLockingFailureException for an update, DuplicateKeyException for two first-time inserts.
     */
    @Override
    public GroupTier save(GroupTier groupTier) {
        try {
            return mongo.save(groupTier);
        } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
            throw new ConcurrentTierChangeException(
                    "This group was changed by someone else at the same moment. Reload and try again.");
        }
    }

    @Override
    public Optional<GroupTier> findById(String groupId) {
        return Optional.ofNullable(mongo.findById(groupId, GroupTier.class));
    }

    @Override
    public List<GroupTier> findByIds(Collection<String> groupIds) {
        if (groupIds.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("_id").in(groupIds)), GroupTier.class);
    }

    @Override
    public List<GroupTier> findAll() {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.ASC, "_id")), GroupTier.class);
    }
}
