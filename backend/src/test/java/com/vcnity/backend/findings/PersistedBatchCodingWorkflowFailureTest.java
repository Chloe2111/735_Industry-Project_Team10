package com.vcnity.backend.findings;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.BatchCodingWorkflow;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.CodingSource;
import com.vcnity.backend.security.TranscriptCoder;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersistedBatchCodingWorkflowFailureTest {

    private static final String TEXT =
            "We need affordable transport to attend community workshops.";

    @Test
    void databaseFailureReturnsControlledFailureReceipt()
            throws InterruptedException {

        FindingRepository repository = mock(FindingRepository.class);

        when(repository.save(any(CodedFinding.class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "Synthetic private database error details"
                ));

        PersistedBatchCodingWorkflow workflow = workflow(
                text -> List.of(new CodingDraft(
                        "Transport access", text, 0.95
                )),
                repository
        );

        PersistedBatchCodingWorkflow.Result result =
                workflow.run(List.of(source()));

        assertFalse(result.allProducedFindingsPersisted());
        assertEquals(1, result.persistenceReceipts().size());

        var receipt = result.persistenceReceipts().get(0);

        assertEquals(
                PersistedBatchCodingWorkflow.PersistenceStatus.STORAGE_FAILED,
                receipt.status()
        );
        assertEquals("FINDING_STORAGE_FAILED", receipt.errorCode());

        // Coding passed, but persistence did not.
        assertEquals(
                BatchCodingWorkflow.RoutingStatus.PENDING_VERIFICATION,
                result.workflowResult().sources().get(0).routingStatus()
        );

        // Preserve the finding so persistence can be retried.
        CodedFinding finding = result.workflowResult().sources()
                .get(0).codingOutcome().findings().get(0);

        assertEquals(finding.itemId(), receipt.itemId());
        assertEquals(finding.sourceRef(), receipt.sourceRef());
    }

    @Test
    void partialFailureKeepsResultsAndRetryReusesOriginalFindings()
            throws InterruptedException {

        FindingRepository repository = mock(FindingRepository.class);
        AtomicInteger coderCalls = new AtomicInteger();

        when(repository.save(any(CodedFinding.class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "Synthetic connection failure"
                ))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TranscriptCoder coder = text -> {
            coderCalls.incrementAndGet();

            return List.of(
                    new CodingDraft("Transport access", text, 0.95),
                    new CodingDraft("Workshop participation", text, 0.95)
            );
        };

        PersistedBatchCodingWorkflow workflow =
                workflow(coder, repository);

        PersistedBatchCodingWorkflow.Result first =
                workflow.run(List.of(source()));

        assertEquals(2, first.persistenceReceipts().size());
        assertEquals(
                PersistedBatchCodingWorkflow.PersistenceStatus.STORAGE_FAILED,
                first.persistenceReceipts().get(0).status()
        );
        assertEquals(
                PersistedBatchCodingWorkflow.PersistenceStatus.SAVED,
                first.persistenceReceipts().get(1).status()
        );
        assertFalse(first.allProducedFindingsPersisted());

        PersistedBatchCodingWorkflow.Result retry =
                workflow.persist(first.workflowResult());

        assertTrue(retry.allProducedFindingsPersisted());
        assertEquals(1, coderCalls.get());

        assertEquals(
                first.persistenceReceipts().stream()
                        .map(receipt -> receipt.itemId())
                        .toList(),
                retry.persistenceReceipts().stream()
                        .map(receipt -> receipt.itemId())
                        .toList()
        );

        List<CodedFinding> originalFindings =
                first.workflowResult().sources().get(0)
                        .codingOutcome().findings();

        for (CodedFinding finding : originalFindings) {
            verify(repository, times(2)).save(finding);
        }
    }

    @Test
    void conflictingIdIsReportedSeparatelyFromDatabaseFailure()
            throws InterruptedException {

        FindingRepository repository = mock(FindingRepository.class);

        when(repository.save(any(CodedFinding.class)))
                .thenThrow(
                        new MongoFindingRepository.FindingConflictException()
                );

        PersistedBatchCodingWorkflow workflow = workflow(
                text -> List.of(new CodingDraft(
                        "Transport access", text, 0.95
                )),
                repository
        );

        PersistedBatchCodingWorkflow.Result result =
                workflow.run(List.of(source()));

        assertFalse(result.allProducedFindingsPersisted());
        assertEquals(1, result.persistenceReceipts().size());

        var receipt = result.persistenceReceipts().get(0);

        assertEquals(
                PersistedBatchCodingWorkflow.PersistenceStatus.CONFLICT,
                receipt.status()
        );
        assertEquals("FINDING_ID_CONFLICT", receipt.errorCode());
    }

    private PersistedBatchCodingWorkflow workflow(
            TranscriptCoder coder,
            FindingRepository repository
    ) {
        return new PersistedBatchCodingWorkflow(
                new BatchCodingWorkflow(
                        coder,
                        new ExceptionsQueueService()
                ),
                repository
        );
    }

    private CodingSource source() {
        return new CodingSource(
                "synthetic_failure_test",
                TEXT,
                2,
                "PERSON_001"
        );
    }
}