package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Keeps submissions in MongoDB (collection "intake_submissions"). This is the default store. */
@Repository
@ConditionalOnProperty(name = "intake.store", havingValue = "mongo", matchIfMissing = true)
public class MongoSubmissionStore implements SubmissionStore {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "submittedAt");

    private final MongoTemplate mongo;

    public MongoSubmissionStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Submission save(Submission submission) {
        return mongo.save(submission);
    }

    @Override
    public Optional<Submission> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Submission.class));
    }

    @Override
    public List<Submission> findByGroupId(String groupId) {
        // "groupIds" is an array field; is(...) matches when any element equals groupId.
        Query query = Query.query(Criteria.where("groupIds").is(groupId)).with(NEWEST_FIRST);
        return mongo.find(query, Submission.class);
    }

    @Override
    public List<Submission> findByStatus(SubmissionStatus status) {
        Query query = Query.query(Criteria.where("status").is(status.name())).with(NEWEST_FIRST);
        return mongo.find(query, Submission.class);
    }
}
