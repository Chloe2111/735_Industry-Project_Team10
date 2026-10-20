package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import com.vcnity.backend.findings.FindingRepository;
import com.vcnity.backend.security.CodedFinding;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * A facilitator codes a community idea that the AI could not code (flag AI_COULD_NOT_CODE).
 *
 * The facilitator gives a short theme and a quote chosen from the idea. The quote must be copied
 * exactly from the de-identified idea text, so nobody can put words in a member's mouth. The
 * result is saved as a finding with codedBy "facilitator" and no AI confidence, and the queue
 * item is cleared. Clearing never publishes: publishing stays a separate step, as for every flag.
 *
 * Order: every check runs first, then the finding is saved, then the item is cleared. A finding
 * is only shown in reports once its submission is published, so a save whose clear then fails
 * shows up nowhere, and sending the same coding again simply finishes the job.
 */
@Service
public class FacilitatorCodingService {

    static final int MAX_THEME_LENGTH = 80;
    static final int MAX_NOTE_LENGTH = 500;
    static final String FINDING_ID_PREFIX = "facilitator-";

    private final ExceptionsQueueService queue;
    private final SubmissionStore submissions;
    private final GroupTierService groupTiers;
    private final FindingRepository findings;

    public FacilitatorCodingService(ExceptionsQueueService queue, SubmissionStore submissions,
                                    GroupTierService groupTiers, FindingRepository findings) {
        this.queue = Objects.requireNonNull(queue);
        this.submissions = Objects.requireNonNull(submissions);
        this.groupTiers = Objects.requireNonNull(groupTiers);
        this.findings = Objects.requireNonNull(findings);
    }

    /**
     * @return the cleared item, or empty if there is no item with this id
     * @throws IllegalArgumentException (400) if the request is wrong: not an AI_COULD_NOT_CODE item,
     *         missing or too-long theme, a quote that is not in the idea text, or a note that is too long
     * @throws ReviewBlockedException (409) if the item was already reviewed, or the submission is held
     *         or its group is Tier 3
     */
    public synchronized Optional<ExceptionItem> code(String itemId, String theme, String quote, String note) {
        Optional<ExceptionItem> found = queue.get(itemId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ExceptionItem item = found.get();

        if (item.getFlagType() != FlagType.AI_COULD_NOT_CODE) {
            throw new IllegalArgumentException(
                    "Only ideas the AI could not code can be coded by a facilitator. Use Clear or Reject for this flag.");
        }

        String cleanTheme = theme == null ? "" : theme.trim();
        String cleanQuote = quote == null ? "" : quote.trim();
        String cleanNote = note == null ? "" : note.trim();
        if (cleanTheme.isEmpty()) {
            throw new IllegalArgumentException("Enter a theme for this idea.");
        }
        if (cleanTheme.length() > MAX_THEME_LENGTH) {
            throw new IllegalArgumentException("The theme must be " + MAX_THEME_LENGTH + " characters or fewer.");
        }
        if (cleanQuote.isEmpty()) {
            throw new IllegalArgumentException("Choose the sentence from the idea that supports the theme.");
        }
        if (cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("The explanation must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }

        Submission submission = submissionOf(item);
        String findingId = FINDING_ID_PREFIX + item.getId();

        if (item.getStatus() != ReviewStatus.PENDING) {
            // Sending the same coding again (for example after a lost connection) is not an error.
            if (item.getStatus() == ReviewStatus.CLEARED
                    && findings.findByItemId(findingId)
                    .filter(f -> f.theme().equals(cleanTheme) && f.quote().equals(cleanQuote))
                    .isPresent()) {
                return Optional.of(item);
            }
            throw new ReviewBlockedException("This idea has already been reviewed. Reload the queue to see it.");
        }
        if (item.getBlockedReason() != null && !item.getBlockedReason().isBlank()) {
            throw new ReviewBlockedException(item.getBlockedReason());
        }
        if (submission.getStatus() == SubmissionStatus.HELD
                || !isCodableTier(submission.getTierAtIntake())
                || !groupTiers.restrictedAmong(submission.getGroupIds()).isEmpty()) {
            throw new ReviewBlockedException(IntakeService.HALTED_REASON);
        }

        String ideaText = submission.getText();
        if (ideaText == null || !ideaText.contains(cleanQuote)) {
            throw new IllegalArgumentException(
                    "The quote must be copied exactly from the idea text. Choose one of the idea's sentences.");
        }

        CodedFinding finding = CodedFinding.codedByFacilitator(
                findingId, submission.getId(), cleanTheme, cleanQuote, submission.getTierAtIntake());
        Optional<CodedFinding> earlier = findings.findByItemId(findingId);
        if (earlier.isPresent() && !earlier.get().equals(finding)) {
            throw new ReviewBlockedException(
                    "A different coding was already saved for this idea. Reload the queue and try again.");
        }
        findings.save(finding);

        return queue.clear(item.getId(), cleanNote);
    }

    /** The community submission the item belongs to. Only those can be coded here. */
    private Submission submissionOf(ExceptionItem item) {
        if (!IntakeService.SOURCE_TYPE.equals(item.getSourceType()) || item.getSourceId() == null) {
            throw new IllegalArgumentException("Only community ideas can be coded by a facilitator.");
        }
        return submissions.findById(item.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The idea for this item could not be found. Reject it with a reason instead."));
    }

    private static boolean isCodableTier(Integer tier) {
        return tier != null && (tier == 1 || tier == 2);
    }
}
