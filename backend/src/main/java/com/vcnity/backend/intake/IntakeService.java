package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ExceptionReviewListener;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.PipelineService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Story 24: connects the community submission form to intake and tiering.
 *
 * Every submission follows the same path:
 *   1. Look up the tier of its group. No tier set -> refused, nothing stored.
 *   2. Tier 3 -> held. The pipeline is not called and the text is not stored.
 *   3. Tier 1 or 2 -> run the pipeline (tier gate, de-identification, coding, grounding).
 *        clean   -> published
 *        flagged -> hidden, and sent to the exceptions queue for a person to review
 *   4. When a reviewer clears every flag the submission is published; a reject keeps it hidden.
 *
 * Nothing is ever published without either passing every check or being cleared by a person.
 */
@Service
public class IntakeService implements ExceptionReviewListener {

    /** Tag on exceptions-queue items raised here, so this class reacts only to its own items. */
    public static final String SOURCE_TYPE = "COMMUNITY_SUBMISSION";

    static final int MAX_TEXT_LENGTH = 5000;
    static final int MAX_GROUPS = 10;

    static final String HALTED_PREFIX = "HALTED - group re-tiered to Tier 3. Do not clear without a human re-check. ";

    private static final System.Logger LOG = System.getLogger(IntakeService.class.getName());

    private final GroupTierService groupTiers;
    private final SubmissionStore submissions;
    private final PipelineRunner pipeline;
    private final ExceptionsQueueService queue;
    private final AtomicBoolean reviewQueueRestored = new AtomicBoolean(false);

    public IntakeService(GroupTierService groupTiers, SubmissionStore submissions,
                         PipelineRunner pipeline, ExceptionsQueueService queue) {
        this.groupTiers = groupTiers;
        this.submissions = submissions;
        this.pipeline = pipeline;
        this.queue = queue;
        queue.addReviewListener(this);
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    public SubmissionReceipt submit(SubmissionRequest request) {
        restoreReviewQueueOnce();
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
        submission.setTierAtIntake(tier);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);

        // Tier 3 stops here, before the pipeline is touched in any way.
        if (tier == Tiers.RESTRICTED) {
            submission.setStatus(SubmissionStatus.HELD);
            submission.setStatusReason("Tier 3 (culturally restricted): not processed automatically and the text was not stored.");
            return receipt(submissions.save(submission));
        }

        PipelineService.PipelineOutcome outcome = runPipeline(submission.getId(), tier, text);
        String stage = outcome.stageReached() == null ? "" : outcome.stageReached();

        switch (stage) {
            case "clean" -> {
                submission.setText(text);
                copyCodingResult(submission, outcome);
                submission.setStatus(SubmissionStatus.PUBLISHED);
                submissions.save(submission);
            }
            case "exceptions_queue" -> {
                submission.setText(text);
                copyCodingResult(submission, outcome);
                submission.setFlags(splitFlags(outcome.reason()));
                submission.setStatus(SubmissionStatus.PENDING_REVIEW);
                submission.setStatusReason("Waiting for a person to review: " + outcome.reason());
                // Saved as hidden first, so a reviewer can never clear an item whose submission is not stored yet.
                submissions.save(submission);
                attachReviewItems(submission);
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
        return receipt(submission);
    }

    public Optional<SubmissionReceipt> getReceipt(String id) {
        restoreReviewQueueOnce();
        return submissions.findById(id).map(this::receipt);
    }

    /** The published submissions of one group, newest first. Hidden ones are never included. */
    public List<PublishedSubmission> listPublished(String groupId) {
        restoreReviewQueueOnce();
        String id = GroupTierService.normaliseGroupId(groupId);
        if (id.isEmpty()) throw new IllegalArgumentException("groupId is required.");
        return submissions.findByGroupId(id).stream()
                .filter(s -> s.getStatus() == SubmissionStatus.PUBLISHED)
                .map(s -> new PublishedSubmission(s.getId(), s.getGroupIds(), s.getText(), s.getTierAtIntake(), s.getSubmittedAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Group tiers
    // ------------------------------------------------------------------

    public List<GroupTier> listGroups() {
        restoreReviewQueueOnce();
        return groupTiers.list();
    }

    /**
     * Sets or changes a group's tier on a person's instruction.
     * If the group becomes Tier 3, every submission of that group still waiting for review is halted.
     */
    public ClassificationResult classifyGroup(TierRequest request) {
        restoreReviewQueueOnce();
        if (request == null) throw new IllegalArgumentException("A group, tier, name and reason are required.");

        Integer previousTier = groupTiers.get(request.groupId()).map(GroupTier::getTier).orElse(null);
        GroupTier updated = groupTiers.setTier(
                request.groupId(), request.groupName(), request.tier(), request.setBy(), request.reason());

        List<String> halted = new ArrayList<>();
        int publishedToRecheck = 0;
        boolean becameRestricted = updated.getTier() == Tiers.RESTRICTED
                && (previousTier == null || previousTier != Tiers.RESTRICTED);
        if (becameRestricted) {
            for (Submission submission : submissions.findByGroupId(updated.getGroupId())) {
                if (submission.getStatus() == SubmissionStatus.PENDING_REVIEW) {
                    halt(submission);
                    halted.add(submission.getId());
                } else if (submission.getStatus() == SubmissionStatus.PUBLISHED) {
                    publishedToRecheck++;
                }
            }
        }
        return new ClassificationResult(updated, halted, publishedToRecheck);
    }

    private void halt(Submission submission) {
        submission.setStatus(SubmissionStatus.HELD);
        submission.setStatusReason("Halted: its group was re-tiered to Tier 3. A person must re-check it.");
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
        for (ExceptionItem item : queue.listBySource(SOURCE_TYPE, submission.getId())) {
            if (item.getStatus() == ReviewStatus.PENDING && !startsWithHaltedPrefix(item.getSourceContext())) {
                item.setSourceContext(HALTED_PREFIX + (item.getSourceContext() == null ? "" : item.getSourceContext()));
            }
        }
    }

    private static boolean startsWithHaltedPrefix(String context) {
        return context != null && context.startsWith(HALTED_PREFIX);
    }

    // ------------------------------------------------------------------
    // Reacting to reviews in the shared exceptions queue
    // ------------------------------------------------------------------

    /**
     * Called by the exceptions queue for every item a reviewer clears or rejects.
     * Items that were not raised by a community submission (for example Story 18 coding findings)
     * are ignored, so reviewing them changes nothing here.
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
            return; // halted or Tier 3: a review in the queue must never publish it
        }
        applyReviewStatus(submission, statusFromReviews(submission.getId(), item.getId(), newStatus));
    }

    /**
     * A submission can have several flags. It is published only when every one is cleared.
     * Any rejected flag keeps it hidden; clearing that flag later brings it back.
     */
    private SubmissionStatus statusFromReviews(String submissionId, String changingItemId, ReviewStatus changingTo) {
        boolean anyPending = false;
        boolean anyRejected = false;
        for (ExceptionItem item : queue.listBySource(SOURCE_TYPE, submissionId)) {
            ReviewStatus status = item.getId().equals(changingItemId) ? changingTo : item.getStatus();
            if (status == ReviewStatus.REJECTED) anyRejected = true;
            if (status == ReviewStatus.PENDING) anyPending = true;
        }
        if (anyRejected) return SubmissionStatus.REJECTED;
        if (anyPending) return SubmissionStatus.PENDING_REVIEW;
        return SubmissionStatus.PUBLISHED;
    }

    private void applyReviewStatus(Submission submission, SubmissionStatus next) {
        if (submission.getStatus() == next) return;
        submission.setStatus(next);
        submission.setStatusReason(switch (next) {
            case PUBLISHED -> null;
            case REJECTED -> "A reviewer rejected this submission. It is hidden but has not been deleted.";
            default -> "Waiting for a person to review: " + String.join(", ", submission.getFlags());
        });
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
    }

    // ------------------------------------------------------------------
    // Keeping the in-memory queue and the stored submissions in step
    // ------------------------------------------------------------------

    /**
     * The exceptions queue lives in memory, so a restart empties it while submissions waiting for
     * review are still stored. The first intake call after a restart puts their review items back.
     */
    private void restoreReviewQueueOnce() {
        if (reviewQueueRestored.get()) return;
        synchronized (this) {
            if (reviewQueueRestored.get()) return;
            restoreReviewQueue();
            reviewQueueRestored.set(true);
        }
    }

    /** Re-creates review items for stored submissions that are waiting for review but have none. */
    int restoreReviewQueue() {
        int restored = 0;
        for (Submission submission : submissions.findByStatus(SubmissionStatus.PENDING_REVIEW)) {
            if (!queue.listBySource(SOURCE_TYPE, submission.getId()).isEmpty()) continue;
            attachReviewItems(submission);
            restored++;
        }
        return restored;
    }

    /** Raises one tagged queue item per flag and records their ids on the submission. */
    private void attachReviewItems(Submission submission) {
        String flags = String.join(", ", submission.getFlags());
        String context = "Community submission flagged by automated pipeline checks (" + flags + ")"
                + (submission.getTheme() == null || submission.getTheme().isBlank()
                        ? "" : " for coded theme \"" + submission.getTheme() + "\"")
                + ". It stays hidden until every flag is cleared. Verify against the de-identified source before clearing.";
        Double confidence = submission.getConfidence();
        if (confidence != null && (confidence.isNaN() || confidence < 0 || confidence > 1)) {
            confidence = null; // the queue only accepts 0..1
        }

        List<String> ids = new ArrayList<>();
        for (FlagType type : queue.mapReason(flags)) {
            ExceptionItem item = new ExceptionItem(null, type, submission.getQuote(), context, confidence, submission.getId());
            item.setSourceType(SOURCE_TYPE);
            item.setSourceId(submission.getId());
            try {
                ids.add(queue.add(item).getId());
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not queue review item for submission {0}: {1}",
                        submission.getId(), e.getMessage());
            }
        }
        submission.setExceptionIds(ids);
        if (ids.isEmpty()) {
            // Never publish and never lose it silently: it stays hidden and says why.
            submission.setStatusReason("Flagged (" + flags + ") but no review item could be created. Needs manual review.");
        }
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
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

    private static String cleanText(SubmissionRequest request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Please write your feedback before submitting.");
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Feedback must be " + MAX_TEXT_LENGTH + " characters or fewer.");
        }
        return text;
    }

    private SubmissionReceipt receipt(Submission submission) {
        String message = switch (submission.getStatus()) {
            case PUBLISHED -> "Thank you. Your feedback has been published to the group.";
            case PENDING_REVIEW -> "Thank you. Your feedback was received and will be checked by a person before it is published.";
            case REJECTED -> "A reviewer decided not to publish this feedback.";
            case HELD -> submission.getStatusReason() == null
                    ? "This feedback was held and was not processed automatically."
                    : submission.getStatusReason();
        };
        return new SubmissionReceipt(
                submission.getId(),
                submission.getStatus(),
                submission.getTierAtIntake(),
                Tiers.label(submission.getTierAtIntake()),
                submission.getGroupIds(),
                message,
                submission.getSubmittedAt());
    }
}
