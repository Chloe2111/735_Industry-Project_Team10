package com.vcnity.backend.intake;

import java.time.Instant;
import java.util.List;

/** A published submission as shown to the group. Hidden submissions are never returned in this shape. */
public record PublishedSubmission(
        String id,
        List<String> groupIds,
        String text,
        Integer tier,
        Instant submittedAt
) {
}
