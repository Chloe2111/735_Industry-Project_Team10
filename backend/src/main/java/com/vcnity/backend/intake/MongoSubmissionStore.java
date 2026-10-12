package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
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
    public List<Submission> findByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return mongo.find(Query.query(inGroupWithStatus(groupId, status)).with(NEWEST_FIRST), Submission.class);
    }

    @Override
    public long countByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return mongo.count(Query.query(inGroupWithStatus(groupId, status)), Submission.class);
    }

    @Override
    public List<Submission> findByStatus(SubmissionStatus status) {
        Query query = Query.query(Criteria.where("status").is(status.name())).with(NEWEST_FIRST);
        return mongo.find(query, Submission.class);
    }

    /** One conditional update: it matches the document only while its status is still {@code from}. */
    @Override
    public boolean changeStatus(String id, SubmissionStatus from, SubmissionStatus to, String reason, Instant at) {
        Update change = new Update()
                .set("status", to.name())
                .set("statusReason", reason)
                .set("updatedAt", Date.from(at));
        Query stillInFrom = Query.query(Criteria.where("_id").is(id).and("status").is(from.name()));
        return mongo.updateFirst(stillInFrom, change, Submission.class).getMatchedCount() > 0;
    }

    /**
     * Two database calls however many submissions there are: one bulk update that only matches
     * documents still in PENDING_REVIEW or REJECTED, then one read of exactly the documents that
     * update changed (they are the HELD ones of this group carrying this call's timestamp).
     */
    @Override
    public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
        Date when = Date.from(at);
        Update halt = new Update()
                .set("status", SubmissionStatus.HELD.name())
                .set("statusReason", reason)
                .set("updatedAt", when);
        Criteria unpublishedInGroup = Criteria.where("groupIds").is(groupId).and("status")
                .in(List.of(SubmissionStatus.PENDING_REVIEW.name(), SubmissionStatus.REJECTED.name()));
        mongo.updateMulti(Query.query(unpublishedInGroup), halt, Submission.class);

        Query haltedByThisCall = Query.query(
                inGroupWithStatus(groupId, SubmissionStatus.HELD).and("updatedAt").is(when));
        return mongo.find(haltedByThisCall, Submission.class);
    }

    /** "groupIds" is an array field; is(...) matches when any element equals groupId. */
    private static Criteria inGroupWithStatus(String groupId, SubmissionStatus status) {
        return Criteria.where("groupIds").is(groupId).and("status").is(status.name());
    }
}
