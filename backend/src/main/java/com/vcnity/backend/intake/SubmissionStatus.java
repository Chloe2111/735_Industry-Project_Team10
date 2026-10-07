package com.vcnity.backend.intake;

/** Where a community submission currently stands. Only PUBLISHED submissions are shown to the group. */
public enum SubmissionStatus {
    /** Passed every automated check, or a reviewer cleared every flag. Visible to the group. */
    PUBLISHED,
    /** Flagged by the pipeline. Hidden while any of its flags is still waiting for a person. */
    PENDING_REVIEW,
    /** Every flag has been reviewed and at least one was rejected. Hidden, but kept so it can be recovered. */
    REJECTED,
    /** Tier 3, or halted because its group became Tier 3. Never coded while held, and cannot be cleared. */
    HELD
}
