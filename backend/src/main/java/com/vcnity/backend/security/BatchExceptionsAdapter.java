package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects checked batch findings to the Story 19 exceptions queue.
 *
 * Queue storage is currently in memory.
 * Callers must inspect the receipt before reporting successful routing.
 */
public final class BatchExceptionsAdapter {

    private final ExceptionsQueueService queue;

    public BatchExceptionsAdapter(ExceptionsQueueService queue) {
        if (queue == null) {
            throw new IllegalArgumentException(
                    "Exceptions queue must not be null"
            );
        }

        this.queue = queue;
    }

    /**
     * Records which entries were successfully added.
     *
     * A failed receipt may contain IDs from earlier successful writes.
     * The current queue does not support atomic multi-entry writes.
     */
    public record QueueReceipt(
            boolean complete,
            List<String> exceptionIds,
            String errorCode
    ) {
        public QueueReceipt {
            exceptionIds = List.copyOf(exceptionIds);
        }
    }

    /**
     * Enqueues the flags of a finding that has already been grounded.
     *
     * sourceContext must be the corresponding eligible,
     * de-identified source text, not the raw transcript.
     *
     * Empty flags mean there is nothing to enqueue.
     * This method does not independently verify grounding or permission.
     */
    public QueueReceipt enqueue(
            CodedFinding finding,
            String sourceContext
    ) {
        if (finding == null) {
            throw new IllegalArgumentException(
                    "Finding must not be null"
            );
        }

        if (finding.flags().isEmpty()) {
            return new QueueReceipt(true, List.of(), null);
        }

        /*
         * Resolve every flag before writing anything.
         * Unsupported flags must not disappear silently.
         */
        List<FlagType> queueFlags = new ArrayList<>();

        for (String flag : finding.flags()) {
            FlagType type = mapFlag(flag);

            if (type == null) {
                return new QueueReceipt(
                        false,
                        List.of(),
                        "UNSUPPORTED_QUEUE_FLAG"
                );
            }

            queueFlags.add(type);
        }

        boolean sourceMissing =
                finding.flags().contains("sourceMissing");

        if (!sourceMissing
                && (sourceContext == null || sourceContext.isBlank())) {
            return new QueueReceipt(
                    false,
                    List.of(),
                    "SOURCE_CONTEXT_REQUIRED"
            );
        }

        List<String> addedIds = new ArrayList<>();

        for (FlagType type : queueFlags) {
            ExceptionItem item = new ExceptionItem(
                    null,
                    type,
                    finding.quote(),
                    sourceContext,
                    finding.confidence(),
                    finding.sourceRef()
            );

            try {
                ExceptionItem saved = queue.add(item);

                if (saved == null
                        || saved.getId() == null
                        || saved.getId().isBlank()) {
                    return new QueueReceipt(
                            false,
                            addedIds,
                            "QUEUE_WRITE_FAILED"
                    );
                }

                addedIds.add(saved.getId());
            } catch (RuntimeException exception) {
                // Do not expose internal exception messages.
                return new QueueReceipt(
                        false,
                        addedIds,
                        "QUEUE_WRITE_FAILED"
                );
            }
        }

        return new QueueReceipt(true, addedIds, null);
    }

    private FlagType mapFlag(String flag) {
        return switch (flag) {
            case "quoteNotGrounded" -> FlagType.QUOTE_NOT_FOUND;
            case "lowConfidence" -> FlagType.LOW_CONFIDENCE;
            case "sourceMissing" -> FlagType.SOURCE_MISSING;
            case "contradiction" -> FlagType.CONTESTED;
            default -> null;
        };
    }
}