package com.vcnity.backend.intake;

import java.util.List;

/**
 * What the submission form sends: the group (or groups), the feedback text and, optionally,
 * the commission the idea answers.
 *
 * There is deliberately no tier field. The tier always comes from the group's own record,
 * so a submitter can never choose or lower it. The commission only says which client request
 * the idea belongs to; it never changes how the idea is handled.
 */
public record SubmissionRequest(List<String> groupIds, String text, String commissionId) {

    /** Feedback that does not answer a particular commission (e.g. Share feedback). */
    public SubmissionRequest(List<String> groupIds, String text) {
        this(groupIds, text, null);
    }
}
