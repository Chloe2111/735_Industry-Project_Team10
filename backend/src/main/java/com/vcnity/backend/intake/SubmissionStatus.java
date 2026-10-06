package com.vcnity.backend.intake;

/** Where a community submission currently stands. Only PUBLISHED submissions are shown to the group. */
public enum SubmissionStatus {
    /** Passed every automated check, or a reviewer cleared every flag. Visible to the group. */
    PUBLISHED,
    /** Flagged by the pipeline. Hidden until a person clears every flag in the exceptions queue. */
    PENDING_REVIEW,
    /** A reviewer rejected a flag. Hidden, but kept so it can be recovered. */
    REJECTED,
    /** Stopped at the tier gate (Tier 3). Never coded, never queued for coding. */
    HELD
}
