package com.vcnity.backend.security;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs batch coding and routes flagged findings to Story 19.
 *
 * The caller must supply eligible, de-identified sources and
 * the application's shared queue service.
 *
 * This workflow does not grant final approval or automatically
 * retry queue writes.
 */
public final class BatchCodingWorkflow {

    private final BatchCodingJob job;
    private final BatchExceptionsAdapter adapter;

    public BatchCodingWorkflow(
            TranscriptCoder coder,
            ExceptionsQueueService queue
    ) {
        this.job = new BatchCodingJob(coder);
        this.adapter = new BatchExceptionsAdapter(queue);
    }

    public enum RoutingStatus {
        PENDING_VERIFICATION,
        PENDING_EXCEPTION_REVIEW,
        NO_FINDINGS,
        CODING_FAILED,
        QUEUE_FAILED
    }

    /**
     * Links each queue receipt to the finding that produced it.
     */
    public record FindingReceipt(
            String itemId,
            BatchExceptionsAdapter.QueueReceipt receipt
    ) {
    }

    /**
     * Retains the coding outcome and any queue-write evidence.
     */
    public record RoutedSource(
            BatchCodingJob.SourceOutcome codingOutcome,
            RoutingStatus routingStatus,
            List<FindingReceipt> queueReceipts
    ) {
        public RoutedSource {
            queueReceipts = List.copyOf(queueReceipts);
        }
    }

    public record WorkflowResult(List<RoutedSource> sources) {
        public WorkflowResult {
            sources = List.copyOf(sources);
        }
    }

    public WorkflowResult run(List<CodingSource> sources)
            throws InterruptedException {

        if (sources == null) {
            throw new IllegalArgumentException(
                    "Sources must not be null"
            );
        }

        List<CodingSource> batch = new ArrayList<>(sources);

        /*
         * The job validates all source records and duplicate references
         * before calling the coder.
         */
        BatchCodingJob.BatchResult batchResult = job.run(batch);

        Map<String, String> sourceLookup = new HashMap<>();

        for (CodingSource source : batch) {
            sourceLookup.put(source.sourceRef(), source.text());
        }

        List<RoutedSource> routedSources = new ArrayList<>();

        for (BatchCodingJob.SourceOutcome outcome
                : batchResult.outcomes()) {

            routedSources.add(route(outcome, sourceLookup));
        }

        return new WorkflowResult(routedSources);
    }

    private RoutedSource route(
            BatchCodingJob.SourceOutcome outcome,
            Map<String, String> sourceLookup
    ) {
        return switch (outcome.status()) {
            case CHECKS_PASSED -> new RoutedSource(
                    outcome,
                    RoutingStatus.PENDING_VERIFICATION,
                    List.of()
            );

            case NO_FINDINGS -> new RoutedSource(
                    outcome,
                    RoutingStatus.NO_FINDINGS,
                    List.of()
            );

            case FAILED -> new RoutedSource(
                    outcome,
                    RoutingStatus.CODING_FAILED,
                    List.of()
            );

            case REQUIRES_REVIEW ->
                    routeFlaggedFindings(outcome, sourceLookup);
        };
    }

    private RoutedSource routeFlaggedFindings(
            BatchCodingJob.SourceOutcome outcome,
            Map<String, String> sourceLookup
    ) {
        List<FindingReceipt> receipts = new ArrayList<>();

        for (CodedFinding finding : outcome.findings()) {
            if (finding.flags().isEmpty()) {
                continue;
            }

            String sourceContext =
                    sourceLookup.get(finding.sourceRef());

            BatchExceptionsAdapter.QueueReceipt receipt =
                    adapter.enqueue(finding, sourceContext);

            receipts.add(new FindingReceipt(
                    finding.itemId(),
                    receipt
            ));

            if (!receipt.complete()) {
                /*
                 * Earlier entries may already exist.
                 * Preserve their receipts and report failed routing.
                 */
                return new RoutedSource(
                        outcome,
                        RoutingStatus.QUEUE_FAILED,
                        receipts
                );
            }
        }

        return new RoutedSource(
                outcome,
                RoutingStatus.PENDING_EXCEPTION_REVIEW,
                receipts
        );
    }
}