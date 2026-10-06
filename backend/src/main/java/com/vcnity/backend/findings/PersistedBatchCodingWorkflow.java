package com.vcnity.backend.findings;

import com.vcnity.backend.security.BatchCodingWorkflow;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingSource;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the existing batch workflow and persists its findings.
 *
 * Inputs must already be eligible and de-identified.
 * Persistence does not grant review or publication approval.
 */
public final class PersistedBatchCodingWorkflow {

    private final BatchCodingWorkflow workflow;
    private final FindingRepository repository;

    public PersistedBatchCodingWorkflow(
            BatchCodingWorkflow workflow,
            FindingRepository repository
    ) {
        this.workflow = Objects.requireNonNull(
                workflow,
                "Workflow must not be null"
        );
        this.repository = Objects.requireNonNull(
                repository,
                "Repository must not be null"
        );
    }

    public enum PersistenceStatus {
        SAVED,
        CONFLICT,
        STORAGE_FAILED
    }

    public record PersistenceReceipt(
            String itemId,
            String sourceRef,
            PersistenceStatus status,
            String errorCode
    ) {
    }

    /**
     * Keeps coding/queue outcomes separate from persistence outcomes.
     *
     * The original findings remain available for a persistence retry.
     */
    public record Result(
            BatchCodingWorkflow.WorkflowResult workflowResult,
            List<PersistenceReceipt> persistenceReceipts
    ) {
        public Result {
            Objects.requireNonNull(
                    workflowResult,
                    "Workflow result must not be null"
            );
            persistenceReceipts = List.copyOf(persistenceReceipts);
        }

        /**
         * True when every finding produced was saved successfully.
         * An empty receipt list means there were no findings to save.
         * This does not establish coding or queue success.
         */
        public boolean allProducedFindingsPersisted() {
            return persistenceReceipts.stream().allMatch(
                    receipt ->
                            receipt.status() == PersistenceStatus.SAVED
            );
        }
    }

    public Result run(List<CodingSource> sources)
            throws InterruptedException {

        BatchCodingWorkflow.WorkflowResult result =
                workflow.run(sources);

        return persist(result);
    }

    /**
     * Saves findings from an existing workflow result.
     *
     * Call this with the original result to retry persistence without
     * rerunning the coder, generating new IDs, or creating queue entries.
     */
    public Result persist(
            BatchCodingWorkflow.WorkflowResult workflowResult
    ) throws InterruptedException {

        Objects.requireNonNull(
                workflowResult,
                "Workflow result must not be null"
        );

        List<PersistenceReceipt> receipts = new ArrayList<>();

        for (BatchCodingWorkflow.RoutedSource source
                : workflowResult.sources()) {

            for (CodedFinding finding
                    : source.codingOutcome().findings()) {

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException(
                            "Finding persistence interrupted"
                    );
                }

                receipts.add(saveFinding(finding));
            }
        }

        return new Result(workflowResult, receipts);
    }

    private PersistenceReceipt saveFinding(CodedFinding finding) {
        try {
            repository.save(finding);

            return receipt(
                    finding,
                    PersistenceStatus.SAVED,
                    null
            );
        } catch (MongoFindingRepository.FindingConflictException exception) {
            return receipt(
                    finding,
                    PersistenceStatus.CONFLICT,
                    "FINDING_ID_CONFLICT"
            );
        } catch (DataAccessException exception) {
            // Return a controlled code rather than database error details.
            return receipt(
                    finding,
                    PersistenceStatus.STORAGE_FAILED,
                    "FINDING_STORAGE_FAILED"
            );
        }
    }

    private PersistenceReceipt receipt(
            CodedFinding finding,
            PersistenceStatus status,
            String errorCode
    ) {
        return new PersistenceReceipt(
                finding.itemId(),
                finding.sourceRef(),
                status,
                errorCode
        );
    }
}