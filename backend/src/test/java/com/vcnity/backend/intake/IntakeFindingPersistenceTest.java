package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.findings.FindingRepository;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.PipelineService;
import com.vcnity.backend.security.TranscriptCoder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntakeFindingPersistenceTest {

    private static final String GROUP = "workshop group";
    private static final String TEXT =
            "The workshop was helpful. We need more seating.";

    private record Fixture(
            IntakeService intake,
            InMemorySubmissionStore submissions,
            ExceptionsQueueService queue,
            AtomicInteger coderCalls) {
    }

    private Fixture fixture(FindingRepository repository, TranscriptCoder coder) {
        AtomicInteger calls = new AtomicInteger();
        PipelineService pipeline = new PipelineService(text -> {
            calls.incrementAndGet();
            return coder.code(text);
        });

        DefaultPipelineRunner runner = new DefaultPipelineRunner(
                pipeline, Set.of(), Set.of(), repository);
        InMemorySubmissionStore submissions = new InMemorySubmissionStore();
        ExceptionsQueueService queue = new ExceptionsQueueService();
        IntakeService intake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()),
                submissions, runner, queue);

        intake.classifyGroup(new TierRequest(
                GROUP, null, 1, "Test reviewer", "Synthetic test"));

        return new Fixture(intake, submissions, queue, calls);
    }

    private List<CodingDraft> twoGroundedFindings() {
        return List.of(
                new CodingDraft("Workshop value", "The workshop was helpful.", 0.95),
                new CodingDraft("Seating", "We need more seating.", 0.95)
        );
    }

    private SubmissionReceipt submit(Fixture fixture) {
        return fixture.intake().submit(new SubmissionRequest(List.of(GROUP), TEXT));
    }

    private void assertNoSubmissionOrReviewItems(Fixture fixture) {
        assertTrue(fixture.submissions().findByGroupId(GROUP).isEmpty());
        assertTrue(fixture.intake().listPublished(GROUP).isEmpty());
        assertTrue(fixture.queue().list().isEmpty());
    }

    @Test
    void savesEveryFindingOnceWithoutRecodingOrDuplicateQueueItems() {
        FindingRepository repository = mock(FindingRepository.class);
        when(repository.save(any(CodedFinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Fixture fixture = fixture(repository, text -> List.of(
                new CodingDraft("Workshop value", "The workshop was helpful.", 0.95),
                new CodingDraft("Transport", "Everyone requested a bus route.", 0.95)
        ));

        SubmissionReceipt receipt = submit(fixture);

        assertEquals(1, fixture.coderCalls().get());
        assertEquals(SubmissionStatus.PENDING_REVIEW, receipt.status());
        assertTrue(fixture.intake().listPublished(GROUP).isEmpty());

        ArgumentCaptor<CodedFinding> saved =
                ArgumentCaptor.forClass(CodedFinding.class);
        verify(repository, times(2)).save(saved.capture());
        verifyNoMoreInteractions(repository);

        List<CodedFinding> findings = saved.getAllValues();
        assertNotEquals(findings.get(0).itemId(), findings.get(1).itemId());
        assertTrue(findings.stream()
                .allMatch(finding -> receipt.id().equals(finding.sourceRef())));
        assertEquals(1, findings.stream()
                .filter(finding -> finding.flags().contains("quoteNotGrounded"))
                .count());

        // Intake owns queue routing; the runner must not enqueue again.
        assertEquals(1, fixture.queue().list().size());
        assertEquals(1, fixture.queue()
                .listBySource(IntakeService.SOURCE_TYPE, receipt.id()).size());
    }

    @Test
    void firstStorageFailurePreventsSubmissionPublication() {
        FindingRepository repository = mock(FindingRepository.class);
        when(repository.save(any(CodedFinding.class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "Private database connection details"));

        Fixture fixture = fixture(repository, text -> twoGroundedFindings());

        PipelineUnavailableException error =
                assertThrows(PipelineUnavailableException.class, () -> submit(fixture));

        assertFalse(error.getMessage().contains("Private database"));
        assertEquals(1, fixture.coderCalls().get());
        verify(repository, times(1)).save(any(CodedFinding.class));
        assertNoSubmissionOrReviewItems(fixture);
    }

    @Test
    void laterStorageFailureLeavesEarlierWriteButPreventsPublication() {
        FindingRepository repository = mock(FindingRepository.class);
        List<CodedFinding> written = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();

        when(repository.save(any(CodedFinding.class))).thenAnswer(invocation -> {
            CodedFinding finding = invocation.getArgument(0);
            if (attempts.incrementAndGet() == 2) {
                throw new DataAccessResourceFailureException("Second write failed");
            }
            written.add(finding);
            return finding;
        });

        Fixture fixture = fixture(repository, text -> twoGroundedFindings());

        assertThrows(PipelineUnavailableException.class, () -> submit(fixture));

        assertEquals(1, written.size(),
                "Earlier writes are not automatically rolled back");
        assertEquals(2, attempts.get());
        assertEquals(1, fixture.coderCalls().get());
        assertNoSubmissionOrReviewItems(fixture);
    }

    @Test
    void missingStorageConfirmationPreventsPublication() {
        FindingRepository repository = mock(FindingRepository.class);
        when(repository.save(any(CodedFinding.class))).thenReturn(null);

        Fixture fixture = fixture(repository, text -> twoGroundedFindings());

        assertThrows(PipelineUnavailableException.class, () -> submit(fixture));
        verify(repository, times(1)).save(any(CodedFinding.class));
        assertNoSubmissionOrReviewItems(fixture);
    }

    private CodedFinding finding(String id, String source) {
        return new CodedFinding(
                id, source, null, "Seating",
                "We need more seating.", 0.95, 1, List.of());
    }

    private DefaultPipelineRunner runnerWithFindings(
            FindingRepository repository, List<CodedFinding> findings)
            throws Exception {
        PipelineService pipeline = mock(PipelineService.class);
        var clean = new PipelineService.PipelineOutcome(
                "SRC-1", "clean", null, "Seating",
                "We need more seating.", 0.95, 0);

        when(pipeline.runPipeline(anyList(), anySet(), anySet()))
                .thenReturn(new PipelineService.PipelineRun(
                        List.of(clean), List.of(clean),
                        List.of(), List.of(), findings));

        return new DefaultPipelineRunner(
                pipeline, Set.of(), Set.of(), repository);
    }

    @Test
    void findingForAnotherSourceIsRejectedBeforeAnyWrite() throws Exception {
        FindingRepository repository = mock(FindingRepository.class);
        var runner = runnerWithFindings(repository, List.of(
                finding("F-1", "SRC-1"),
                finding("F-2", "OTHER-SOURCE")
        ));

        assertThrows(IllegalStateException.class,
                () -> runner.run("SRC-1", 1, TEXT));
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateFindingIdsAreRejectedBeforeAnyWrite() throws Exception {
        FindingRepository repository = mock(FindingRepository.class);
        var runner = runnerWithFindings(repository, List.of(
                finding("F-1", "SRC-1"),
                finding("F-1", "SRC-1")
        ));

        assertThrows(IllegalStateException.class,
                () -> runner.run("SRC-1", 1, TEXT));
        verifyNoInteractions(repository);
    }
}
