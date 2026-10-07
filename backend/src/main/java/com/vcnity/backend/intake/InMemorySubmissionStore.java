package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps submissions in memory. Used by the tests, and by the app when started with
 * {@code intake.store=memory} (no MongoDB needed; everything is lost on restart).
 * Stores and returns copies, like a real database would.
 */
@Repository
@ConditionalOnProperty(name = "intake.store", havingValue = "memory")
public class InMemorySubmissionStore implements SubmissionStore {

    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();

    @Override
    public Submission save(Submission submission) {
        submissions.put(submission.getId(), submission.copy());
        return submission;
    }

    @Override
    public Optional<Submission> findById(String id) {
        Submission found = id == null ? null : submissions.get(id);
        return found == null ? Optional.empty() : Optional.of(found.copy());
    }

    @Override
    public List<Submission> findByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return submissions.values().stream()
                .filter(s -> s.getGroupIds().contains(groupId) && s.getStatus() == status)
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .map(Submission::copy)
                .toList();
    }

    @Override
    public long countByGroupIdAndStatus(String groupId, SubmissionStatus status) {
        return submissions.values().stream()
                .filter(s -> s.getGroupIds().contains(groupId) && s.getStatus() == status)
                .count();
    }

    @Override
    public List<Submission> findByStatus(SubmissionStatus status) {
        return submissions.values().stream()
                .filter(s -> s.getStatus() == status)
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .map(Submission::copy)
                .toList();
    }

    @Override
    public boolean changeStatus(String id, SubmissionStatus from, SubmissionStatus to, String reason, Instant at) {
        boolean[] changed = new boolean[1];
        submissions.computeIfPresent(id, (key, stored) -> {
            if (stored.getStatus() != from) return stored;
            Submission next = stored.copy();
            next.setStatus(to);
            next.setStatusReason(reason);
            next.setUpdatedAt(at);
            changed[0] = true;
            return next;
        });
        return changed[0];
    }

    @Override
    public List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at) {
        List<Submission> halted = new ArrayList<>();
        for (String id : List.copyOf(submissions.keySet())) {
            // computeIfPresent re-checks the status at the moment of the change, as the bulk update does.
            submissions.computeIfPresent(id, (key, stored) -> {
                boolean unpublished = stored.getStatus() == SubmissionStatus.PENDING_REVIEW
                        || stored.getStatus() == SubmissionStatus.REJECTED;
                if (!unpublished || !stored.getGroupIds().contains(groupId)) {
                    return stored;
                }
                Submission next = stored.copy();
                next.setStatus(SubmissionStatus.HELD);
                next.setStatusReason(reason);
                next.setUpdatedAt(at);
                halted.add(next.copy());
                return next;
            });
        }
        return halted;
    }
}
