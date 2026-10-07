package com.vcnity.backend.exceptions.service;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.security.PipelineService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ExceptionsQueueService {
    static final int MAX_NOTE_LENGTH = 500;

    private final Map<String, ExceptionItem> queue = new ConcurrentHashMap<>();
    private final List<ExceptionReviewListener> reviewListeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener that is told about every clear/reject before it is stored.
     * Listeners must check the item's sourceType and ignore items that are not theirs.
     */
    public void addReviewListener(ExceptionReviewListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        reviewListeners.add(listener);
    }

    /** Every item (any status) raised for one record of one feature, e.g. one community submission. */
    public List<ExceptionItem> listBySource(String sourceType, String sourceId) {
        if (sourceType == null || sourceId == null) return List.of();
        return list().stream()
                .filter(i -> sourceType.equals(i.getSourceType()) && sourceId.equals(i.getSourceId()))
                .toList();
    }

    public List<ExceptionItem> list() {
        return queue.values().stream()
                .sorted(Comparator.comparing(ExceptionItem::getCreatedAt).reversed())
                .toList();
    }

    public List<ExceptionItem> listPending() {
        return list().stream().filter(i -> i.getStatus() == ReviewStatus.PENDING).toList();
    }

    public Optional<ExceptionItem> get(String id) {
        return Optional.ofNullable(queue.get(id));
    }

    public ExceptionItem add(ExceptionItem item) {
        validate(item);
        if (item.getId() == null || item.getId().isBlank()) item.setId(UUID.randomUUID().toString());
        item.setStatus(ReviewStatus.PENDING);
        item.setCreatedAt(LocalDateTime.now());
        item.setReviewedAt(null);
        queue.put(item.getId(), item);
        return item;
    }

    public Optional<ExceptionItem> clear(String id, String note) {
        return review(id, ReviewStatus.CLEARED, note);
    }

    public Optional<ExceptionItem> reject(String id, String note) {
        return review(id, ReviewStatus.REJECTED, note);
    }

    private synchronized Optional<ExceptionItem> review(String id, ReviewStatus status, String note) {
        String cleanNote = note == null ? "" : note.trim();
        // A rejection must carry a reason so there is an audit trail for why a finding was dropped.
        if (status == ReviewStatus.REJECTED && cleanNote.isEmpty()) {
            throw new IllegalArgumentException("A reviewer note is required to reject an exception");
        }
        if (cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Reviewer note must be " + MAX_NOTE_LENGTH + " characters or fewer");
        }
        ExceptionItem item = queue.get(id);
        if (item == null) return Optional.empty();
        // Listeners run first. If one fails, the item keeps its old status so the reviewer can retry,
        // instead of the queue saying "cleared" while the follow-up action never happened.
        for (ExceptionReviewListener listener : reviewListeners) {
            listener.beforeReview(item, status);
        }
        item.setStatus(status);
        item.setReviewerNote(cleanNote);
        item.setReviewedAt(LocalDateTime.now());
        return Optional.of(item);
    }

    /**
     * Imports every item a pipeline run routed to the exceptions queue.
     * The context text is built from the de-identified pipeline result only, never the raw source.
     */
    public List<ExceptionItem> addFromPipelineRun(PipelineService.PipelineRun run) {
        if (run == null || run.exceptions() == null) return List.of();
        List<ExceptionItem> added = new ArrayList<>();
        for (PipelineService.PipelineOutcome outcome : run.exceptions()) {
            String context = "Flagged by automated pipeline checks (" + outcome.reason() + ")"
                    + (outcome.theme() == null ? "" : " for coded theme \"" + outcome.theme() + "\"")
                    + ". Verify against the de-identified source before clearing.";
            added.addAll(addFromPipelineOutcome(outcome, context));
        }
        return added;
    }

    /**
     * sourceContext must already be de-identified. Skips a flag when the same item already has
     * the same flag pending, so re-running the pipeline does not create duplicate reviews.
     */
    public List<ExceptionItem> addFromPipelineOutcome(PipelineService.PipelineOutcome outcome, String sourceContext) {
        if (outcome == null || !"exceptions_queue".equals(outcome.stageReached())) return List.of();
        List<ExceptionItem> added = new ArrayList<>();
        for (FlagType type : mapReason(outcome.reason())) {
            boolean alreadyPending = queue.values().stream().anyMatch(i ->
                    i.getStatus() == ReviewStatus.PENDING
                            && i.getFlagType() == type
                            && java.util.Objects.equals(i.getSourceRef(), outcome.itemId()));
            if (alreadyPending) continue;
            added.add(add(new ExceptionItem(null, type, outcome.quote(), sourceContext,
                    outcome.confidence(), outcome.itemId())));
        }
        return added;
    }

    public List<FlagType> mapReason(String reason) {
        if (reason == null || reason.isBlank()) return List.of();
        List<FlagType> result = new ArrayList<>();
        if (reason.contains("quoteNotGrounded")) result.add(FlagType.QUOTE_NOT_FOUND);
        if (reason.contains("lowConfidence")) result.add(FlagType.LOW_CONFIDENCE);
        if (reason.contains("sourceMissing")) result.add(FlagType.SOURCE_MISSING);
        if (reason.contains("contradiction")) result.add(FlagType.CONTESTED);
        if (reason.contains("tierViolation")) result.add(FlagType.TIER_VIOLATION);
        if (reason.contains("REPRESENTATION_GAP")) result.add(FlagType.REPRESENTATION_GAP);
        return result;
    }

    private void validate(ExceptionItem item) {
        if (item == null) throw new IllegalArgumentException("Exception item is required");
        if (item.getFlagType() == null) throw new IllegalArgumentException("flagType is required");
        if (item.getSourceRef() == null || item.getSourceRef().isBlank()) {
            throw new IllegalArgumentException("sourceRef is required");
        }
        if (item.getConfidence() != null && (item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public void clearAllForTests() { queue.clear(); }
}
