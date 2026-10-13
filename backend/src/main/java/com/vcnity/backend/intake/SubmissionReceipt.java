package com.vcnity.backend.intake;

import java.time.Instant;
import java.util.List;

/** What the submitter gets back: the tier their feedback was handled under and what happened to it. */
public record SubmissionReceipt(
        String id,
        SubmissionStatus status,
        Integer tier,
        List<String> groupIds,
        String message,
        Instant submittedAt
) {
}
