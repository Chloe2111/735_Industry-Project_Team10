package com.vcnity.backend.intake;

import java.util.List;

/**
 * The outcome of setting a group's tier.
 *
 * haltedSubmissionIds: submissions that were waiting for review and were halted because the group
 * became Tier 3. publishedToRecheck: how many already-published submissions exist for the group
 * from before it became Tier 3, so a person can re-check them.
 */
public record ClassificationResult(GroupTier group, List<String> haltedSubmissionIds, int publishedToRecheck) {
}
