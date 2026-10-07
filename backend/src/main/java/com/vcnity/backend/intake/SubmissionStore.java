package com.vcnity.backend.intake;

import java.util.List;
import java.util.Optional;

/** Where submissions are kept. MongoDB in the running app; an in-memory version for tests and demos. */
public interface SubmissionStore {

    Submission save(Submission submission);

    Optional<Submission> findById(String id);

    /** Every submission that belongs to the group, newest first. */
    List<Submission> findByGroupId(String groupId);

    List<Submission> findByStatus(SubmissionStatus status);
}
