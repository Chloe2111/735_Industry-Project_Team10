package com.vcnity.backend.intake;

import java.util.List;

/**
 * The outcome of setting a group's tier.
 *
 * haltedSubmissionIds: submissions that were waiting for review, or had been rejected, and were
 * halted because the group became Tier 3. publishedToRecheck: how many already-published submissions
 * the group has from before it became Tier 3. They are hidden while the group is Tier 3, and a
 * person should re-check them.
 */
public record ClassificationResult(GroupTier group, List<String> haltedSubmissionIds, int publishedToRecheck) {
}
