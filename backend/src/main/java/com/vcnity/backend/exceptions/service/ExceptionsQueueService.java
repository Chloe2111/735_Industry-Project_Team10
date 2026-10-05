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

@Service
public class ExceptionsQueueService {
    private final Map<String, ExceptionItem> queue = new ConcurrentHashMap<>();

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

    private Optional<ExceptionItem> review(String id, ReviewStatus status, String note) {
        ExceptionItem item = queue.get(id);
        if (item == null) return Optional.empty();
        item.setStatus(status);
        item.setReviewerNote(note == null ? "" : note.trim());
        item.setReviewedAt(LocalDateTime.now());
        return Optional.of(item);
    }

    public List<ExceptionItem> addFromPipelineOutcome(PipelineService.PipelineOutcome outcome, String sourceContext) {
        if (outcome == null || !"exceptions_queue".equals(outcome.stageReached())) return List.of();
        List<ExceptionItem> added = new ArrayList<>();
        for (FlagType type : mapReason(outcome.reason())) {
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
