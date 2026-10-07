package com.vcnity.backend.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes eligible, de-identified sources through a supplied coder.
 *
 * Upstream processing must establish consent and review eligibility.
 * Passing checks does not grant final publication approval.
 */
public final class BatchCodingJob {

    private final TranscriptCoder coder;

    public BatchCodingJob(TranscriptCoder coder) {
        if (coder == null) {
            throw new IllegalArgumentException(
                    "Coder must not be null"
            );
        }

        this.coder = coder;
    }

    public enum Status {
        CHECKS_PASSED,
        REQUIRES_REVIEW,
        NO_FINDINGS,
        FAILED
    }

    /**
     * One outcome per source.
     *
     * errorCode contains a controlled code, not raw exception text.
     * Findings retain their own grounding flags.
     */
    public record SourceOutcome(
            String sourceRef,
            Status status,
            List<CodedFinding> findings,
            String errorCode
    ) {
        public SourceOutcome {
            findings = List.copyOf(findings);
        }
    }

    public record BatchResult(List<SourceOutcome> outcomes) {
        public BatchResult {
            outcomes = List.copyOf(outcomes);
        }
    }

    /**
     * Validates the full source list before calling the coder.
     *
     * Ordinary coding failures produce a FAILED source outcome.
     * Interruption stops the batch and propagates to the caller.
     */
    public BatchResult run(List<CodingSource> sources)
            throws InterruptedException {

        if (sources == null) {
            throw new IllegalArgumentException(
                    "Sources must not be null"
            );
        }

        List<CodingSource> batch = new ArrayList<>(sources);
        Map<String, String> sourceLookup = new HashMap<>();

        for (CodingSource source : batch) {
            if (source == null) {
                throw new IllegalArgumentException(
                        "Sources must not contain null entries"
                );
            }

            if (sourceLookup.containsKey(source.sourceRef())) {
                throw new IllegalArgumentException(
                        "Duplicate source references are not allowed"
                );
            }

            sourceLookup.put(source.sourceRef(), source.text());
        }

        Map<String, String> fixedLookup = Map.copyOf(sourceLookup);
        List<SourceOutcome> outcomes = new ArrayList<>();

        for (CodingSource source : batch) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(
                        "Batch coding interrupted"
                );
            }

            outcomes.add(processSource(source, fixedLookup));
        }

        return new BatchResult(outcomes);
    }

    private SourceOutcome processSource(
            CodingSource source,
            Map<String, String> sourceLookup
    ) throws InterruptedException {

        List<CodingDraft> drafts;

        try {
            drafts = coder.code(source.text());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            // Do not expose the coder's original exception message.
            throw new InterruptedException(
                    "Batch coding interrupted"
            );
        } catch (Exception exception) {
            return failed(source, "CODER_FAILED");
        }

        if (drafts == null) {
            return failed(source, "INVALID_CODER_OUTPUT");
        }

        if (drafts.isEmpty()) {
            return new SourceOutcome(
                    source.sourceRef(),
                    Status.NO_FINDINGS,
                    List.of(),
                    null
            );
        }

        /*
         * Validate every draft for this source before grounding any.
         * If one draft is malformed, return an explicit source failure
         * rather than silently keeping only the valid drafts.
         */
        List<CodedFinding> candidates = new ArrayList<>();

        try {
            for (CodingDraft draft : drafts) {
                if (draft == null) {
                    return failed(source, "INVALID_CODER_OUTPUT");
                }

                candidates.add(new CodedFinding(
                        UUID.randomUUID().toString(),
                        source.sourceRef(),
                        source.speakerCode(),
                        draft.theme(),
                        draft.quote(),
                        draft.confidence(),
                        source.tier(),
                        List.of()
                ));
            }
        } catch (IllegalArgumentException exception) {
            return failed(source, "INVALID_CODER_OUTPUT");
        }

        List<CodedFinding> checkedFindings = new ArrayList<>();
        boolean reviewRequired = false;

        try {
            for (CodedFinding candidate : candidates) {
                Grounding.GroundingResult result =
                        Grounding.groundFinding(
                                candidate,
                                sourceLookup
                        );

                List<String> flags = result.flags()
                        .asMap()
                        .entrySet()
                        .stream()
                        .filter(entry ->
                                Boolean.TRUE.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();

                checkedFindings.add(candidate.withFlags(flags));

                if (!flags.isEmpty()) {
                    reviewRequired = true;
                }
            }
        } catch (RuntimeException exception) {
            return failed(source, "GROUNDING_FAILED");
        }

        return new SourceOutcome(
                source.sourceRef(),
                reviewRequired
                        ? Status.REQUIRES_REVIEW
                        : Status.CHECKS_PASSED,
                checkedFindings,
                null
        );
    }

    private SourceOutcome failed(
            CodingSource source,
            String errorCode
    ) {
        return new SourceOutcome(
                source.sourceRef(),
                Status.FAILED,
                List.of(),
                errorCode
        );
    }
}