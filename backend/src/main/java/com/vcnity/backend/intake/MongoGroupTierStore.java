package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

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

    @Override
    public GroupTier save(GroupTier groupTier) {
        return mongo.save(groupTier);
    }

    @Override
    public Optional<GroupTier> findById(String groupId) {
        return Optional.ofNullable(mongo.findById(groupId, GroupTier.class));
    }

    @Override
    public List<GroupTier> findAll() {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.ASC, "_id")), GroupTier.class);
    }
}
