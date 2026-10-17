package com.vcnity.backend.intake;

import com.vcnity.backend.security.Deidentify;
import com.vcnity.backend.findings.FindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.vcnity.backend.security.PipelineService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Combines all outcomes for a submission.
 * A review flag or rejection must never be hidden by a clean outcome.
 *
 * When the AI could not code the text (the call failed, its answer was unusable, or it found no
 * themes), the result is a review outcome with the reason "aiCouldNotCode", so a person reads the
 * de-identified text and codes or rejects it. It is never treated as publishable.
 */
@Component
public class DefaultPipelineRunner implements PipelineRunner {

    /** Review reason for text the AI could not code. Maps to FlagType.AI_COULD_NOT_CODE. */
    public static final String AI_COULD_NOT_CODE = "aiCouldNotCode";

    private static final System.Logger LOG = System.getLogger(DefaultPipelineRunner.class.getName());

    private final PipelineService pipeline;
    private final FindingRepository findings;
    private final Set<String> gazetteer;
    private final Set<String> vernacularTerms;

    @Autowired
    public DefaultPipelineRunner(FindingRepository findings) {
        this(
                new PipelineService(),
                Deidentify.loadTermListFromClasspath("communityGazetteer.txt"),
                Deidentify.loadTermListFromClasspath("vernacularTerms.txt"),
                findings
        );
    }

    // Allows tests to supply controlled pipeline results without calling an AI API.
    DefaultPipelineRunner(PipelineService pipeline,
                          Set<String> gazetteer,
                          Set<String> vernacularTerms,
                          FindingRepository findings) {
        this.pipeline = Objects.requireNonNull(pipeline);
        this.findings = Objects.requireNonNull(findings);
        this.gazetteer = Set.copyOf(gazetteer);
        this.vernacularTerms = Set.copyOf(vernacularTerms);
    }

    public boolean isMockMode() {
        return pipeline.isMockMode();
    }

    @Override
    public PipelineService.PipelineOutcome run(
            String itemId, int tier, String text) throws Exception {
        PipelineService.PipelineItem item =
                new PipelineService.PipelineItem(itemId, tier, text, null);

        PipelineService.PipelineRun result =
                pipeline.runPipeline(List.of(item), gazetteer, vernacularTerms);

        if (result == null || result.outcomes() == null) {
            throw new IllegalStateException("The pipeline returned no outcomes");
        }

        List<PipelineService.PipelineOutcome> matching = result.outcomes().stream()
                .filter(Objects::nonNull)
                .filter(outcome -> Objects.equals(itemId, outcome.itemId()))
                .toList();

        if (matching.isEmpty()) {
            throw new IllegalStateException("The pipeline returned no matching outcome");
        }

        // A gate rejection always takes priority over clean or review outcomes.
        for (var outcome : matching) {
            if ("rejected_at_gate".equals(outcome.stageReached())) {
                return outcome;
            }
        }

        // An unexpected stage must not accidentally permit publication.
        for (var outcome : matching) {
            if (!"clean".equals(outcome.stageReached())
                    && !"exceptions_queue".equals(outcome.stageReached())
                    && !"no_findings".equals(outcome.stageReached())
                    && !"coding_failed".equals(outcome.stageReached())) {
                throw new IllegalStateException("Unexpected pipeline outcome stage");
            }
            if ("clean".equals(outcome.stageReached())
                    && outcome.reason() != null && !outcome.reason().isBlank()) {
                throw new IllegalStateException("Clean outcome contains an unresolved reason");
            }
        }

        List<PipelineService.PipelineOutcome> flagged = matching.stream()
                .filter(outcome -> "exceptions_queue".equals(outcome.stageReached()))
                .toList();

        // The AI could not code the text: it failed, or it found nothing and no other check flagged it.
        // A person reads the de-identified text instead. Never publishable, never silently dropped.
        boolean codingFailed = matching.stream()
                .anyMatch(outcome -> "coding_failed".equals(outcome.stageReached()));
        boolean nothingFound = flagged.isEmpty() && matching.stream()
                .anyMatch(outcome -> "no_findings".equals(outcome.stageReached()));
        boolean aiCouldNotCode = codingFailed || nothingFound;

        if (flagged.isEmpty() && !aiCouldNotCode) {
            persistFindings(itemId, result);
            return matching.get(0);
        }

        Set<String> reasons = new LinkedHashSet<>();
        if (aiCouldNotCode) {
            reasons.add(AI_COULD_NOT_CODE);
            LOG.log(System.Logger.Level.WARNING, "The AI could not code submission {0} ({1}); sent for human review.",
                    itemId, matching.stream()
                            .filter(o -> "coding_failed".equals(o.stageReached()) || "no_findings".equals(o.stageReached()))
                            .map(o -> o.reason() == null ? o.stageReached() : o.reason())
                            .distinct().toList());
        }
        for (var outcome : flagged) {
            if (outcome.reason() == null || outcome.reason().isBlank()) {
                throw new IllegalStateException("Flagged outcome has no review reason");
            }
            boolean hasReason = false;
            for (String part : outcome.reason().split(",")) {
                if (!part.isBlank()) {
                    reasons.add(part.trim());
                    hasReason = true;
                }
            }
            if (!hasReason) {
                throw new IllegalStateException("Flagged outcome has no review reason");
            }
        }

        int redactions = matching.stream()
                .mapToInt(PipelineService.PipelineOutcome::redactionCount)
                .max()
                .orElse(0);

        persistFindings(itemId, result);

        if (aiCouldNotCode) {
            // No AI theme, quote or confidence to show: the reviewer works from the source text.
            return new PipelineService.PipelineOutcome(
                    itemId, "exceptions_queue", String.join(", ", reasons),
                    null, null, null, redactions
            );
        }

        // The legacy outcome holds one evidence tuple; retain it from a flagged
        // outcome rather than combining unrelated quote/confidence fields.
        var evidence = flagged.get(0);
        return new PipelineService.PipelineOutcome(
                itemId, "exceptions_queue", String.join(", ", reasons),
                evidence.theme(), evidence.quote(), evidence.confidence(), redactions
        );
    }

    /**
     * Same call and same term lists as the de-identification step inside PipelineService.
     * Intake stores and publishes only this de-identified text.
     */
    @Override
    public String deidentify(String text) {
        return Deidentify.deidentify(text, gazetteer, vernacularTerms).redactedText();
    }

    /**
     * Saves existing findings only; never reruns coding or writes to the queue.
     * Any failure propagates before intake receives a publishable outcome.
     */
    private void persistFindings(
            String sourceRef, PipelineService.PipelineRun result) {
        Set<String> ids = new LinkedHashSet<>();

        // Validate the complete list before performing any writes.
        for (var finding : result.findings()) {
            if (!Objects.equals(sourceRef, finding.sourceRef())) {
                throw new IllegalStateException(
                        "Pipeline returned a finding for another source");
            }
            if (!ids.add(finding.itemId())) {
                throw new IllegalStateException(
                        "Pipeline returned duplicate finding IDs");
            }
        }

        for (var finding : result.findings()) {
            var saved = findings.save(finding);
            if (!finding.equals(saved)) {
                throw new IllegalStateException(
                        "Finding storage did not confirm the expected record");
            }
        }
    }
}
