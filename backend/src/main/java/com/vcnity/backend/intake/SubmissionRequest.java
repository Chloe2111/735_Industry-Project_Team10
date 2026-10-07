package com.vcnity.backend.intake;

import java.util.List;

/**
 * What the submission form sends: the group (or groups) and the feedback text.
 *
 * There is deliberately no tier field. The tier always comes from the group's own record,
 * so a submitter can never choose or lower it.
 */
public record SubmissionRequest(List<String> groupIds, String text) {
}
