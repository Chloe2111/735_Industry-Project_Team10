package com.vcnity.backend.intake;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Passes every call to another store. Tests extend it and override one method to count calls,
 * simulate a failure, or slip another change in at an exact moment.
 */
class DelegatingSubmissionStore implements SubmissionStore {

    private final SubmissionStore target;

    DelegatingSubmissionStore(SubmissionStore target) {
        this.target = target;
    }

    @Override
    public Submission save(Submission submission) {
        return target.save(submission);
    }

    @Override
    public Optional<Submission> findById(String id) {
        return target.findById(id);
    }

    @Override
    public List<Submission> findByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return target.findByGroupIdAndStatus(groupId, status);
    }

    @Override
    public long countByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return target.countByGroupIdAndStatus(groupId, status);
    }

    @Override
    public List<Submission> findByStatus(SubmissionStatus status) {
        return target.findByStatus(status);
    }

    @Override
    public boolean changeStatus(String id, SubmissionStatus from, SubmissionStatus to, String reason, Instant at) {
        return target.changeStatus(id, from, to, reason, at);
    }

    @Override
    public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
        return target.haltUnpublishedForGroup(groupId, reason, at);
    }
}
