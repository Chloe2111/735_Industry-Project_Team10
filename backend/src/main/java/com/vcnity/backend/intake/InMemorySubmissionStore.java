package com.vcnity.backend.intake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

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
    public List<Submission> findByGroupId(String groupId) {
        return submissions.values().stream()
                .filter(s -> s.getGroupIds().contains(groupId))
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .map(Submission::copy)
                .toList();
    }

    @Override
    public List<Submission> findByStatus(SubmissionStatus status) {
        return submissions.values().stream()
                .filter(s -> s.getStatus() == status)
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .map(Submission::copy)
                .toList();
    }
}
