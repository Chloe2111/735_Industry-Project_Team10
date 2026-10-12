package com.vcnity.backend.intake;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Where submissions are kept. MongoDB in the running app; an in-memory version for tests and demos. */
public interface SubmissionStore {

    Submission save(Submission submission);

    Optional<Submission> findById(String id);

    /** Submissions of one group in one status, newest first. */
    List<Submission> findByGroupIdAndStatus(String groupId, SubmissionStatus status);

    long countByGroupIdAndStatus(String groupId, SubmissionStatus status);

    List<Submission> findByStatus(SubmissionStatus status);

    /**
     * Changes a submission's status only if it is still in the status the caller saw.
     * This is what stops a review from publishing a submission that was halted a moment earlier.
     *
     * @return false if the submission was no longer in {@code from} (nothing was changed)
     */
    boolean changeStatus(String id, SubmissionStatus from, SubmissionStatus to, String reason, Instant at);

    /**
     * Halts every submission of the group that is not published: the ones waiting for review and the
     * ones a reviewer rejected (a rejected one could otherwise be cleared and published later).
     * Sets them to HELD with the given reason, in one bulk update. A submission that left those
     * statuses a moment earlier is not touched.
     *
     * @return the submissions that were halted by this call
     */
    List<Submission> haltUnpublishedForGroup(String groupId, String reason, Instant at);
}
