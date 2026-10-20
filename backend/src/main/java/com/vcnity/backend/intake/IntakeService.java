package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionReviewListener;
import com.vcnity.backend.exceptions.service.ExceptionSourceValidator;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import com.vcnity.backend.security.PipelineService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Story 24: connects the community submission form to intake and tiering.
 *
 * Every submission follows the same path:
 *   1. Look up the tier of its group. No tier set -> refused, nothing stored.
 *   2. Tier 3 -> held. The pipeline is not called. The text is kept for a person to review and is
 *        never returned by any endpoint.
 *   3. Tier 1 or 2 -> run the pipeline (tier gate, de-identification, coding, grounding).
 *        clean   -> published
 *        flagged -> hidden, and sent to the exceptions queue for a person to review
 *      Only the de-identified text is stored and published, never the original.
 *   4. Clearing a flag only resolves that flag. When every flag is cleared the submission is "cleared"
 *        but still hidden; it is published only when a reviewer then publishes it (a separate action).
 *        A reject keeps it hidden.
 *   5. While a group is Tier 3 nothing of that group is shown or can be published, including what
 *        was published or rejected before the group became Tier 3.
 *
 * Nothing is ever published without either passing every check or being cleared by a person.
 */
@Service
public class IntakeService implements ExceptionReviewListener, ExceptionSourceValidator {

    /** Tag on exceptions-queue items raised here, so this class reacts only to its own items. */
    public static final String SOURCE_TYPE = "COMMUNITY_SUBMISSION";

    static final int MAX_TEXT_LENGTH = 5000;
    static final int MAX_COMMISSION_ID_LENGTH = 100;
    static final int MAX_GROUPS = 10;

    static final String HALTED_REASON =
            "Halted: its group was re-tiered to Tier 3. A person must re-check it; it cannot be cleared for publishing.";

    static final String TIER3_HELD_MESSAGE =
            "This group is Tier 3, so your feedback was not processed automatically and is not published."
                    + " It has been kept for a person to review.";

    static final String CLEARED_REASON =
            "Every flag was cleared by a reviewer. It stays hidden until a reviewer publishes it.";

    /** Shown to the reviewer for a submission the AI could not code. */
    static final String AI_COULD_NOT_CODE_CONTEXT =
            "The AI could not code this community submission (it failed or found no themes)."
            + " The evidence is the de-identified text. Reject it with a reason, or code it yourself."
            + " Clearing a flag does not publish it.";

    static final String AI_COULD_NOT_CODE_STATUS =
            "The AI could not code this feedback. Waiting for a person to review it.";

    static final String HIDDEN_BY_TIER3_MESSAGE =
            "This feedback was published, but its group is now Tier 3, so it is hidden until a person re-checks it.";

    private static final System.Logger LOG = System.getLogger(IntakeService.class.getName());

    private final GroupTierService groupTiers;
    private final SubmissionStore submissions;
    private final PipelineRunner pipeline;
    private final ExceptionsQueueService queue;

    public IntakeService(GroupTierService groupTiers, SubmissionStore submissions,
                         PipelineRunner pipeline, ExceptionsQueueService queue) {
        this.groupTiers = groupTiers;
        this.submissions = submissions;
        this.pipeline = pipeline;
        this.queue = queue;
        queue.addReviewListener(this);
        queue.addSourceValidator(this);
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    public SubmissionReceipt submit(SubmissionRequest request) {
        List<String> groupIds = cleanGroupIds(request);
        String text = cleanText(request);

        GroupTierService.TierResolution resolution = groupTiers.resolve(groupIds);
        if (!resolution.isClassified()) {
            throw new UnclassifiedGroupException(resolution.unclassifiedGroupIds());
        }
        int tier = resolution.tier();

        Instant now = Instant.now();
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setGroupIds(groupIds);
        submission.setCommissionId(cleanCommissionId(request));
        submission.setTierAtIntake(tier);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);

        // Tier 3 stops here, before the pipeline is touched in any way: no tier gate, no
        // de-identification, no coding. The text is kept as submitted so a person can review it.
        if (tier == Tiers.RESTRICTED) {
            return holdAsTier3(submission, text);
        }

        PipelineService.PipelineOutcome outcome = runPipeline(submission.getId(), tier, text);
        String stage = outcome.stageReached() == null ? "" : outcome.stageReached();

        // The pipeline can take seconds. If a person moved the group to Tier 3 in the meantime, the
        // result is thrown away and the submission is held exactly like any other Tier 3 submission.
        if (!groupTiers.restrictedAmong(groupIds).isEmpty()) {
            submission.setTierAtIntake(Tiers.RESTRICTED);
            return holdAsTier3(submission, text);
        }

        switch (stage) {
            case "clean" -> {
                submission.setText(deidentified(text));
                copyCodingResult(submission, outcome);
                submission.setStatus(SubmissionStatus.PUBLISHED);
                submissions.save(submission);
            }
            case "exceptions_queue" -> {
                submission.setText(deidentified(text));
                copyCodingResult(submission, outcome);
                submission.setFlags(splitFlags(outcome.reason()));
                submission.setStatus(SubmissionStatus.PENDING_REVIEW);
                submission.setStatusReason(submission.getFlags().contains(DefaultPipelineRunner.AI_COULD_NOT_CODE)
                        ? AI_COULD_NOT_CODE_STATUS
                        : "Waiting for a person to review: " + outcome.reason());
                if (reviewFlagTypes(submission).isEmpty()) {
                    // Never publish and never lose it silently: it stays hidden and says why.
                    submission.setStatusReason("Flagged (" + outcome.reason()
                            + ") but no review item could be created. Needs manual review.");
                }
                // Saved as hidden first, so a reviewer can never clear an item whose submission is not stored yet.
                submissions.save(submission);
                boolean expectedItems = !reviewFlagTypes(submission).isEmpty();
                if (expectedItems && raiseReviewItems(submission).isEmpty()) {
                    // The queue refused every item. Say so on the record instead of leaving it looking normal.
                    submission.setStatusReason("Flagged (" + outcome.reason()
                            + ") but no review item could be created. Needs manual review.");
                    submissions.save(submission);
                }
            }
            default -> {
                // "rejected_at_gate" or anything unexpected: fail closed. Not coded, not published, text not stored.
                submission.setStatus(SubmissionStatus.HELD);
                submission.setStatusReason(outcome.reason() == null || outcome.reason().isBlank()
                        ? "Stopped before automated processing."
                        : outcome.reason());
                submissions.save(submission);
            }
        }
        // The tier was re-read after the pipeline and is not Tier 3, so the receipt needs no further lookup.
        return receipt(submission, false);
    }

    /** Tier 3: held, text kept as submitted for a person to review, no coding result, no queue item. */
    private SubmissionReceipt holdAsTier3(Submission submission, String text) {
        submission.setText(text);
        submission.setStatus(SubmissionStatus.HELD);
        submission.setStatusReason(TIER3_HELD_MESSAGE);
        return receipt(submissions.save(submission), false);
    }

    public Optional<SubmissionReceipt> getReceipt(String id) {
        return submissions.findById(id).map(s -> receipt(s, isHiddenByCurrentTier(s)));
    }

    /**
     * The published submissions of one group, newest first. Hidden ones are never included.
     *
     * A group that is Tier 3 now shows nothing, even what was published before it became Tier 3.
     * The same goes for a post shared with several groups when any of them is Tier 3 now.
     * Those posts are hidden, not deleted or changed: if a person moves the group back, they show again.
     * Two database queries at most, however many posts there are.
     */
    public List<PublishedSubmission> listPublished(String groupId) {
        String id = GroupTierService.normaliseGroupId(groupId);
        if (id.isEmpty()) throw new IllegalArgumentException("groupId is required.");

        List<Submission> published = submissions.findByGroupIdAndStatus(id, SubmissionStatus.PUBLISHED);
        Set<String> groupsToCheck = new LinkedHashSet<>();
        groupsToCheck.add(id);
        for (Submission submission : published) {
            groupsToCheck.addAll(submission.getGroupIds());
        }
        Set<String> restrictedNow = groupTiers.restrictedAmong(groupsToCheck);
        return published.stream()
                .filter(s -> !isTier3AtIntake(s))
                .filter(s -> s.getGroupIds().stream().noneMatch(restrictedNow::contains))
                .map(s -> new PublishedSubmission(s.getId(), s.getGroupIds(), s.getText(), s.getTierAtIntake(), s.getSubmittedAt(),
                        s.getCommissionId()))
                .toList();
    }

    /** Tier 3 submissions hold raw text. Whatever their status says, they are never listed or published. */
    private static boolean isTier3AtIntake(Submission submission) {
        return submission.getTierAtIntake() != null && submission.getTierAtIntake() == Tiers.RESTRICTED;
    }

    /** True for a published submission whose group (or one of its groups) is Tier 3 right now. */
    private boolean isHiddenByCurrentTier(Submission submission) {
        return submission.getStatus() == SubmissionStatus.PUBLISHED
                && !groupTiers.restrictedAmong(submission.getGroupIds()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Group tiers
    // ------------------------------------------------------------------

    public List<GroupTier> listGroups() {
        return groupTiers.list();
    }

    /**
     * Sets or changes a group's tier on a person's instruction.
     *
     * If the group becomes Tier 3, every submission of that group that is waiting for review or was
     * rejected is halted, so none of them can be cleared and published afterwards. Submissions that
     * were already published are hidden for as long as the group is Tier 3 (see listPublished).
     *
     * @throws ConcurrentTierChangeException if someone else changed the group since the person looked at it
     */
    public ClassificationResult classifyGroup(TierRequest request) {
        if (request == null) throw new IllegalArgumentException("A group, tier, name and reason are required.");

        GroupTier updated = groupTiers.setTier(request.groupId(), request.groupName(), request.tier(),
                request.setBy(), request.reason(), request.expectedVersion());

        // Runs whenever the group is Tier 3 after the change, not only when it has just become Tier 3.
        // Halting twice changes nothing, and it means a halt that failed earlier is repaired by saving again.
        if (updated.getTier() != Tiers.RESTRICTED) {
            return new ClassificationResult(updated, List.of(), 0);
        }

        List<Submission> halted = submissions.haltUnpublishedForGroup(
                updated.getGroupId(), HALTED_REASON, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        for (Submission submission : halted) {
            blockReviewItems(submission.getId());
        }
        long published = submissions.countByGroupIdAndStatus(updated.getGroupId(), SubmissionStatus.PUBLISHED);
        return new ClassificationResult(updated, halted.stream().map(Submission::getId).toList(), (int) published);
    }

    /**
     * Marks every queue item of the submission, whatever its review status, so the queue itself
     * refuses to clear it. A rejected item is marked too: clearing it later would publish the submission.
     * The quote is removed from the item as well, so the queue no longer shows text of a Tier 3 group.
     */
    private void blockReviewItems(String submissionId) {
        for (ExceptionItem item : queue.listBySource(SOURCE_TYPE, submissionId)) {
            item.setBlockedReason(HALTED_REASON);
            item.setSourceQuote(null);
        }
    }

    // ------------------------------------------------------------------
    // Reacting to reviews in the shared exceptions queue
    // ------------------------------------------------------------------

    /**
     * Called by the exceptions queue for every item a reviewer clears or rejects.
     * Items that were not raised by a community submission (for example Story 18 coding findings)
     * are ignored, so reviewing them changes nothing here.
     *
     * @throws ReviewBlockedException if the submission is held and the reviewer tried to clear the item.
     *         The queue then leaves the item as it was and the reviewer sees the reason, not a success.
     */
    @Override
    public void beforeReview(ExceptionItem item, ReviewStatus newStatus) {
        if (!SOURCE_TYPE.equals(item.getSourceType()) || item.getSourceId() == null) {
            return;
        }
        Optional<Submission> found = submissions.findById(item.getSourceId());
        if (found.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Reviewed queue item {0} names unknown submission {1}",
                    item.getId(), item.getSourceId());
            return;
        }
        Submission submission = found.get();
        if (submission.getStatus() == SubmissionStatus.HELD) {
            if (newStatus == ReviewStatus.CLEARED) {
                throw new ReviewBlockedException(submission.getStatusReason() == null
                        ? HALTED_REASON : submission.getStatusReason());
            }
            return; // rejecting the item of a held submission is fine: it stays hidden either way
        }
        SubmissionStatus next = statusFromReviews(submission.getId(), item.getId(), newStatus);
        // Clearing a flag never publishes, and it never un-publishes either: a submission that a
        // reviewer already published stays published if one of its flags is cleared again.
        if (submission.getStatus() == SubmissionStatus.PUBLISHED && next == SubmissionStatus.CLEARED) {
            return;
        }
        if (next == submission.getStatus()) {
            return;
        }
        // Checked against the group's tier as it is now, not as it was at intake: a submission that
        // was waiting or rejected when its group became Tier 3 must not be made ready for publishing
        // by a later clear, even if halting it was missed or has not happened yet.
        if (next == SubmissionStatus.CLEARED
                && (isTier3AtIntake(submission) || !groupTiers.restrictedAmong(submission.getGroupIds()).isEmpty())) {
            throw new ReviewBlockedException(HALTED_REASON);
        }
        // Conditional update: if the submission was halted (or otherwise changed) since it was read above,
        // nothing is written and the review is stopped, rather than publishing over the newer state.
        boolean changed = submissions.changeStatus(
                submission.getId(), submission.getStatus(), next, reasonFor(next, submission), Instant.now());
        if (!changed) {
            throw new ReviewBlockedException(
                    "This submission changed while you were reviewing it. Reload the queue and try again.");
        }
    }

    /**
     * Publishes a submission whose flags have all been cleared. This is the only way a flagged
     * submission is ever published: clearing a flag does not do it.
     *
     * Publishing an already published submission changes nothing and is not an error.
     *
     * @return empty if there is no such submission
     * @throws PublishRefusedException if a flag is still pending or was rejected, the submission is
     *         held, or its group is Tier 3 now. Nothing is changed.
     */
    public Optional<SubmissionReceipt> publish(String submissionId) {
        Optional<Submission> found = submissions.findById(submissionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Submission submission = found.get();
        if (submission.getStatus() == SubmissionStatus.PUBLISHED) {
            return Optional.of(receipt(submission, isHiddenByCurrentTier(submission)));
        }
        if (isTier3AtIntake(submission) || !groupTiers.restrictedAmong(submission.getGroupIds()).isEmpty()) {
            throw new PublishRefusedException(HALTED_REASON);
        }
        if (submission.getStatus() != SubmissionStatus.CLEARED) {
            throw new PublishRefusedException(whyNotPublishable(submission));
        }
        // The queue is checked as well as the stored status, in case a flag changed a moment ago.
        if (statusFromReviews(submission.getId(), null, null) != SubmissionStatus.CLEARED) {
            throw new PublishRefusedException(
                    "A flag on this submission is no longer cleared. Reload the queue and review it again.");
        }
        // Conditional update: it applies only while the submission is still cleared, so a halt or a
        // reject that happens at the same moment wins and nothing is published over it.
        boolean changed = submissions.changeStatus(
                submission.getId(), SubmissionStatus.CLEARED, SubmissionStatus.PUBLISHED, null, Instant.now());
        if (!changed) {
            throw new PublishRefusedException(
                    "This submission changed while you were publishing it. Reload the queue and try again.");
        }
        return submissions.findById(submission.getId()).map(s -> receipt(s, false));
    }

    private String whyNotPublishable(Submission submission) {
        return switch (submission.getStatus()) {
            case PENDING_REVIEW -> {
                long pending = queue.listBySource(SOURCE_TYPE, submission.getId()).stream()
                        .filter(item -> item.getStatus() == ReviewStatus.PENDING).count();
                yield (pending == 1 ? "1 flag" : pending + " flags") + " on this submission still "
                        + (pending == 1 ? "needs" : "need") + " review. It can be published once every flag is cleared.";
            }
            case REJECTED -> "A reviewer rejected a flag on this submission, so it cannot be published.";
            case HELD -> submission.getStatusReason() == null ? HALTED_REASON : submission.getStatusReason();
            default -> "This submission cannot be published.";
        };
    }

    /**
     * A submission can have several flags, and nothing is decided until a person has looked at all of them:
     *   any flag still pending   -> still waiting for review (a reject elsewhere does not hide that)
     *   all reviewed, any reject -> rejected (hidden, but kept; clearing that flag later makes it publishable again)
     *   all cleared              -> cleared: ready for a reviewer to publish, but NOT published
     *
     * This never returns PUBLISHED. Only {@link #publish} publishes.
     */
    private SubmissionStatus statusFromReviews(String submissionId, String changingItemId, ReviewStatus changingTo) {
        boolean anyPending = false;
        boolean anyRejected = false;
        for (ExceptionItem item : queue.listBySource(SOURCE_TYPE, submissionId)) {
            ReviewStatus status = item.getId().equals(changingItemId) ? changingTo : item.getStatus();
            if (status == ReviewStatus.PENDING) anyPending = true;
            if (status == ReviewStatus.REJECTED) anyRejected = true;
        }
        if (anyPending) return SubmissionStatus.PENDING_REVIEW;
        if (anyRejected) return SubmissionStatus.REJECTED;
        return SubmissionStatus.CLEARED;
    }

    private static String reasonFor(SubmissionStatus status, Submission submission) {
        return switch (status) {
            case PUBLISHED -> null;
            case CLEARED -> CLEARED_REASON;
            case REJECTED -> "A reviewer rejected this submission. It is hidden but has not been deleted.";
            default -> "Waiting for a person to review: " + String.join(", ", submission.getFlags());
        };
    }

    /**
     * Checks a source tag on an item posted through POST /api/exceptions: it must name a submission
     * that exists and is waiting for review. Anything else is refused, so nobody can post an item
     * that would later hide a published submission or point at one that was never made.
     */
    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public void validateSource(String sourceId) {
        boolean waiting = submissions.findById(sourceId)
                .map(s -> s.getStatus() == SubmissionStatus.PENDING_REVIEW)
                .orElse(false);
        if (!waiting) {
            throw new IllegalArgumentException("No community submission " + sourceId + " is waiting for review.");
        }
    }

    // ------------------------------------------------------------------
    // Keeping the in-memory queue and the stored submissions in step
    // ------------------------------------------------------------------

    /**
     * The exceptions queue lives in memory, so a restart empties it while submissions waiting for
     * review are still stored. This runs once when the app starts and puts their review items back.
     *
     * If the database cannot be reached at that moment the app still starts, and this is logged:
     * restart the backend once the database is back so the items are restored.
     */
    @PostConstruct
    void restoreReviewQueueAtStartup() {
        try {
            int restored = restoreReviewQueue();
            if (restored > 0) {
                LOG.log(System.Logger.Level.INFO, "Restored review items for {0} submission(s) waiting for review", restored);
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Could not restore the review queue at startup ({0}). Restart once the database is available.",
                    e.getMessage());
        }
    }

    /**
     * Re-creates review items for stored submissions that are waiting for review, or cleared but
     * not yet published, and have none. Two database queries, whatever the number of submissions.
     */
    int restoreReviewQueue() {
        Set<String> alreadyQueued = queue.sourceIds(SOURCE_TYPE);
        int restored = 0;
        for (Submission submission : submissions.findByStatus(SubmissionStatus.PENDING_REVIEW)) {
            if (alreadyQueued.contains(submission.getId())) continue;
            if (!raiseReviewItems(submission).isEmpty()) restored++;
        }
        // Cleared but not yet published: put the items back as cleared, so a reviewer still has
        // something to open and publish from. They must not come back as pending.
        for (Submission submission : submissions.findByStatus(SubmissionStatus.CLEARED)) {
            if (alreadyQueued.contains(submission.getId())) continue;
            List<String> ids = raiseReviewItems(submission);
            for (ExceptionItem item : queue.listBySource(SOURCE_TYPE, submission.getId())) {
                if (!ids.contains(item.getId())) continue;
                item.setStatus(ReviewStatus.CLEARED);
                item.setReviewerNote("Cleared before the last restart.");
                item.setReviewedAt(java.time.LocalDateTime.now());
            }
            if (!ids.isEmpty()) restored++;
        }
        return restored;
    }

    /** The queue flag types this submission's pipeline flags map to. */
    private List<FlagType> reviewFlagTypes(Submission submission) {
        return queue.mapReason(String.join(", ", submission.getFlags()));
    }

    /** Raises one tagged queue item per flag. Returns the ids of the items created. */
    private List<String> raiseReviewItems(Submission submission) {
        String flags = String.join(", ", submission.getFlags());
        String context = "Community submission flagged by automated pipeline checks (" + flags + ")"
                + (submission.getTheme() == null || submission.getTheme().isBlank()
                        ? "" : " for coded theme \"" + submission.getTheme() + "\"")
                + ". Clearing a flag does not publish it. Verify against the de-identified source before clearing.";
        Double confidence = submission.getConfidence();
        if (confidence != null && (confidence.isNaN() || confidence < 0 || confidence > 1)) {
            confidence = null; // the queue only accepts 0..1
        }

        List<String> ids = new ArrayList<>();
        for (FlagType type : reviewFlagTypes(submission)) {
            // With no AI quote to check, the reviewer needs the (de-identified) text itself.
            boolean notCoded = type == FlagType.AI_COULD_NOT_CODE;
            ExceptionItem item = new ExceptionItem(null, type,
                    notCoded ? submission.getText() : submission.getQuote(),
                    notCoded ? AI_COULD_NOT_CODE_CONTEXT : context,
                    notCoded ? null : confidence, submission.getId());
            item.setSourceType(SOURCE_TYPE);
            item.setSourceId(submission.getId());
            try {
                ids.add(queue.add(item).getId());
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not queue review item for submission {0}: {1}",
                        submission.getId(), e.getMessage());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PipelineService.PipelineOutcome runPipeline(String id, int tier, String text) {
        PipelineService.PipelineOutcome outcome;
        try {
            outcome = pipeline.run(id, tier, text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PipelineUnavailableException(unavailableMessage(), e);
        } catch (Exception e) {
            throw new PipelineUnavailableException(unavailableMessage(), e);
        }
        if (outcome == null) {
            throw new PipelineUnavailableException(unavailableMessage(), null);
        }
        return outcome;
    }

    /** De-identification is part of the automated checks: if it fails, nothing is saved or published. */
    private String deidentified(String text) {
        String safe;
        try {
            safe = pipeline.deidentify(text);
        } catch (RuntimeException e) {
            throw new PipelineUnavailableException(unavailableMessage(), e);
        }
        if (safe == null) {
            throw new PipelineUnavailableException(unavailableMessage(), null);
        }
        return safe;
    }

    private static String unavailableMessage() {
        return "Your submission could not be completed and has not been published. Please try again later.";
    }

    private static void copyCodingResult(Submission submission, PipelineService.PipelineOutcome outcome) {
        submission.setTheme(outcome.theme());
        submission.setQuote(outcome.quote());
        submission.setConfidence(outcome.confidence());
    }

    private static List<String> splitFlags(String reason) {
        if (reason == null || reason.isBlank()) return List.of();
        return Arrays.stream(reason.split(",")).map(String::trim).filter(flag -> !flag.isEmpty()).distinct().toList();
    }

    private static List<String> cleanGroupIds(SubmissionRequest request) {
        if (request == null || request.groupIds() == null) {
            throw new IllegalArgumentException("Choose the group this feedback is for.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : request.groupIds()) {
            String id = GroupTierService.normaliseGroupId(raw);
            if (!id.isEmpty()) ids.add(id);
        }
        if (ids.isEmpty()) throw new IllegalArgumentException("Choose the group this feedback is for.");
        if (ids.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("A submission can belong to at most " + MAX_GROUPS + " groups.");
        }
        return new ArrayList<>(ids);
    }

    /** Optional. Blank means the feedback does not answer a particular commission. */
    private static String cleanCommissionId(SubmissionRequest request) {
        String id = request.commissionId() == null ? "" : request.commissionId().trim();
        if (id.isEmpty()) return null;
        if (id.length() > MAX_COMMISSION_ID_LENGTH) {
            throw new IllegalArgumentException("That commission reference is not valid.");
        }
        return id;
    }

    private static String cleanText(SubmissionRequest request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Please write your feedback before submitting.");
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Feedback must be " + MAX_TEXT_LENGTH + " characters or fewer.");
        }
        return text;
    }

    /**
     * @param hiddenByTier true when the submission is stored as published but its group is Tier 3 now.
     *                     The submitter is then told it is held, not that it is published.
     */
    private SubmissionReceipt receipt(Submission submission, boolean hiddenByTier) {
        if (hiddenByTier) {
            return new SubmissionReceipt(submission.getId(), SubmissionStatus.HELD, submission.getTierAtIntake(),
                    submission.getGroupIds(), HIDDEN_BY_TIER3_MESSAGE, submission.getSubmittedAt(),
                    submission.getCommissionId());
        }
        String message = switch (submission.getStatus()) {
            case PUBLISHED -> "Thank you. Your feedback has been published to the group."
                    + " Names and contact details that were detected have been removed.";
            case PENDING_REVIEW -> "Thank you. Your feedback was received and will be checked by a person before it is published.";
            case CLEARED -> "A reviewer has checked your feedback. It will appear once a reviewer publishes it.";
            case REJECTED -> "A reviewer decided not to publish this feedback.";
            case HELD -> submission.getStatusReason() == null
                    ? "This feedback was held and was not processed automatically."
                    : submission.getStatusReason();
        };
        return new SubmissionReceipt(
                submission.getId(),
                submission.getStatus(),
                submission.getTierAtIntake(),
                submission.getGroupIds(),
                message,
                submission.getSubmittedAt(),
                submission.getCommissionId());
    }
}
