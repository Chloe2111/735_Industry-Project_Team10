package com.vcnity.backend.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;

/**
 * PipelineService.java
 *
 * Wires the three guardrail classes (TierGate, Deidentify, Grounding)
 * into one end-to-end pipeline: intake -> tier check -> de-identify ->
 * AI coding -> grounding check -> clean output / exceptions queue.
 *
 * Uses the real Claude API if an API key is configured. Otherwise
 * falls back to a clearly-labelled mock coder so the demo runs out of
 * the box without a key.
 *
 * NOTE ON SPRING INTEGRATION: this class is written in plain Java
 * (java.net.http.HttpClient, no Spring imports) so it compiles and is
 * testable standalone. To wire it into the Spring Boot app, add
 * {@code @Service} above the class declaration and inject the API key
 * via {@code @Value("${anthropic.api.key:}")} instead of reading the
 * environment variable directly — both are one-line changes once this
 * sits inside the actual Maven project where Spring is on the classpath.
 */
public class PipelineService {

    private final boolean mockMode;
    private final TranscriptCoder suppliedCoder;
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PipelineService() {
        this.suppliedCoder = null;
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        this.mockMode = (apiKey == null || apiKey.isBlank());
    }

    /**
     * Supplies a coder explicitly, including deterministic coders for tests.
     * This constructor does not read an API key or select a fallback coder.
     */
    public PipelineService(TranscriptCoder coder) {
        this.suppliedCoder = java.util.Objects.requireNonNull(coder);
        this.apiKey = null;
        this.mockMode = false;
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public record PipelineItem(
            String itemId,
            Integer tier,
            String rawText,
            Map<String, Object> mockProfile,
            String speakerCode) {

        public PipelineItem {
            if (speakerCode != null && speakerCode.isBlank()) {
                throw new IllegalArgumentException(
                        "speakerCode must be null or non-blank"
                );
            }
        }

        // Existing callers have no speaker code; preserve that absence.
        public PipelineItem(
                String itemId, Integer tier, String rawText,
                Map<String, Object> mockProfile) {
            this(itemId, tier, rawText, mockProfile, null);
        }
    }

    public enum CoverageStatus {
        NOT_ASSESSED,
        PARTIAL,
        GAPS,
        COMPLETE
    }

    /**
     * Coverage describes speaker inclusion, not grounding or human approval.
     * Unknown speaker codes prevent a COMPLETE assessment.
     */
    public record CoverageSummary(
            CoverageStatus status,
            Set<String> missingSpeakers,
            Set<String> sourcesWithoutSpeakerCodes) {

        public CoverageSummary {
            missingSpeakers = Set.copyOf(missingSpeakers);
            sourcesWithoutSpeakerCodes = Set.copyOf(sourcesWithoutSpeakerCodes);
        }

        static CoverageSummary notAssessed() {
            return new CoverageSummary(
                    CoverageStatus.NOT_ASSESSED, Set.of(), Set.of()
            );
        }
    }

    public record PipelineOutcome(
            String itemId,
            String stageReached, // "rejected_at_gate" | "clean" | "exceptions_queue"
            String reason,
            String theme,
            String quote,
            Double confidence,
            int redactionCount) {
    }

    public record PipelineRun(
            List<PipelineOutcome> outcomes,
            List<PipelineOutcome> clean,
            List<PipelineOutcome> exceptions,
            List<PipelineOutcome> rejected,
            List<CodedFinding> findings,
            CoverageSummary coverage) {

        public PipelineRun {
            outcomes = List.copyOf(outcomes);
            clean = List.copyOf(clean);
            exceptions = List.copyOf(exceptions);
            rejected = List.copyOf(rejected);
            findings = List.copyOf(findings);
            java.util.Objects.requireNonNull(coverage);
        }

        public PipelineRun(
                List<PipelineOutcome> outcomes,
                List<PipelineOutcome> clean,
                List<PipelineOutcome> exceptions,
                List<PipelineOutcome> rejected,
                List<CodedFinding> findings) {
            this(outcomes, clean, exceptions, rejected, findings,
                    CoverageSummary.notAssessed());
        }

        // Compatibility for existing callers and test fixtures.
        public PipelineRun(
                List<PipelineOutcome> outcomes,
                List<PipelineOutcome> clean,
                List<PipelineOutcome> exceptions,
                List<PipelineOutcome> rejected) {
            this(outcomes, clean, exceptions, rejected, List.of());
        }
    }

    // ---------------------------------------------------------------
    // Transcription stub — audio/video items would be transcribed by
    // Whisper here, AFTER TierGate and BEFORE de-identification.
    // ---------------------------------------------------------------
    private String transcribe(PipelineItem item) {
        return item.rawText();
    }

    // ---------------------------------------------------------------
    // AI coding step
    // ---------------------------------------------------------------

    private static final String CODING_PROMPT_TEMPLATE = """
            You are coding a single piece of workshop feedback for a thematic \
            analysis pipeline. Given the text below, return ONLY a JSON object (no other text) with:
              - "theme": a short theme label (a few words)
              - "quote": a verbatim short quote from the text below that supports the theme (must be an \
            exact substring of the text, not paraphrased)
              - "confidence": a number between 0 and 1

            TEXT:
            \"\"\"%s\"\"\"

            Return ONLY the JSON object.
            """;

    private Map<String, Object> mockCode(String text, Map<String, Object> mockProfile) {
        if (mockProfile != null) {
            return mockProfile;
        }
        String firstSentence = text.split("\\.")[0].trim() + ".";
        Map<String, Object> result = new HashMap<>();
        result.put("theme", "General feedback");
        result.put("quote", firstSentence);
        result.put("confidence", 0.9);
        return result;
    }

    private Map<String, Object> codeItem(String text, Map<String, Object> mockProfile) throws IOException, InterruptedException {
        if (mockMode) {
            return mockCode(text, mockProfile);
        }

        String prompt = String.format(CODING_PROMPT_TEMPLATE, text);
        String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String body = """
                {
                  "model": "claude-sonnet-4-6",
                  "max_tokens": 300,
                  "messages": [{"role": "user", "content": "%s"}]
                }
                """.formatted(escapedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Minimal, dependency-free JSON field extraction. If your actual
        // Maven project already has Jackson (spring-boot-starter-web
        // pulls it in), swap this for ObjectMapper — it will be more
        // robust than these regexes.
        String rawText = extractJsonStringField(response.body(), "text");
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("theme", extractJsonStringField(rawText, "theme"));
        parsed.put("quote", extractJsonStringField(rawText, "quote"));
        String confidenceStr = extractJsonNumberField(rawText, "confidence");
        parsed.put("confidence", confidenceStr != null ? Double.parseDouble(confidenceStr) : 0.0);
        return parsed;
    }

    private static String extractJsonStringField(String json, String field) {
        if (json == null) return "";
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String extractJsonNumberField(String json, String field) {
        if (json == null) return null;
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ---------------------------------------------------------------
    // Pipeline orchestration
    // ---------------------------------------------------------------

    public PipelineRun runPipeline(List<PipelineItem> items, Set<String> gazetteer, Set<String> vernacularTerms)
            throws IOException, InterruptedException {

        List<PipelineOutcome> outcomes = new ArrayList<>();
        List<CodedFinding> findings = new ArrayList<>();
        Map<String, String> eligibleSpeakers = new HashMap<>();
        Set<String> sourcesWithoutSpeakers = new HashSet<>();

        for (PipelineItem item : items) {
            String itemId = item.itemId() != null ? item.itemId() : "<unknown>";

            // Stage 1: tier gate — the ONLY place Tier 3 / untiered items are checked
            Map<String, Object> tierGateInput = new HashMap<>();
            tierGateInput.put("itemId", itemId);
            tierGateInput.put("tier", item.tier());
            try {
                TierGate.tierGate(tierGateInput);
            } catch (TierGate.TierGateException e) {
                outcomes.add(new PipelineOutcome(itemId, "rejected_at_gate", e.getReasonText(), null, null, null, 0));
                continue;
            }

            // Coverage includes only sources that passed the tier gate.
            if (item.speakerCode() == null) {
                sourcesWithoutSpeakers.add(itemId);
            } else {
                eligibleSpeakers.put(itemId, item.speakerCode());
            }

            // Stage 2: transcription (stub for text items)
            String rawText = transcribe(item);

            // Stage 3: de-identification — passes BOTH term lists,
            // including the new vernacular list (DEF-1 fix)
            Deidentify.DeidentificationResult deidResult = Deidentify.deidentify(rawText, gazetteer, vernacularTerms);
            String cleanText = deidResult.redactedText();

            // Stages 4 and 5 share Story 18's validation and grounding.
            // Carry the actual speaker code, including null when unknown.
            CodingSource source = new CodingSource(
                    itemId, cleanText, item.tier(), item.speakerCode()
            );

            TranscriptCoder coder = suppliedCoder != null
                    ? suppliedCoder
                    : deidentifiedText -> {
                        Map<String, Object> coded =
                                codeItem(deidentifiedText, item.mockProfile());
                        Object confidence = coded.get("confidence");
                        return List.of(new CodingDraft(
                                (String) coded.get("theme"),
                                (String) coded.get("quote"),
                                confidence instanceof Number
                                        ? ((Number) confidence).doubleValue()
                                        : null
                        ));
                    };

            BatchCodingJob.SourceOutcome sourceOutcome =
                    new BatchCodingJob(coder)
                            .run(List.of(source))
                            .outcomes().get(0);

            findings.addAll(sourceOutcome.findings());

            switch (sourceOutcome.status()) {
                case FAILED -> outcomes.add(new PipelineOutcome(
                        itemId, "coding_failed", sourceOutcome.errorCode(),
                        null, null, null, deidResult.entityCount()
                ));
                case NO_FINDINGS -> outcomes.add(new PipelineOutcome(
                        itemId, "no_findings", null,
                        null, null, null, deidResult.entityCount()
                ));
                case CHECKS_PASSED, REQUIRES_REVIEW -> {
                    for (CodedFinding finding : sourceOutcome.findings()) {
                        boolean flagged = !finding.flags().isEmpty();
                        outcomes.add(new PipelineOutcome(
                                itemId,
                                flagged ? "exceptions_queue" : "clean",
                                flagged ? String.join(", ", finding.flags()) : null,
                                finding.theme(),
                                finding.quote(),
                                finding.confidence(),
                                deidResult.entityCount()
                        ));
                    }
                }
            }
        }

        // Compare real eligible speaker codes with actual produced findings.
        Set<String> expectedSpeakers = new HashSet<>(eligibleSpeakers.values());
        List<CodedItem> codedItems = findings.stream()
                .map(finding -> new CodedItem(
                        finding.itemId(), finding.speakerCode()
                ))
                .toList();

        CoverageResult coverageResult =
                CoverageCheck.checkCoverage(expectedSpeakers, codedItems);

        CoverageStatus coverageStatus;
        if (expectedSpeakers.isEmpty()) {
            coverageStatus = CoverageStatus.NOT_ASSESSED;
        } else if (!coverageResult.isComplete()) {
            coverageStatus = CoverageStatus.GAPS;
        } else if (!sourcesWithoutSpeakers.isEmpty()) {
            coverageStatus = CoverageStatus.PARTIAL;
        } else {
            coverageStatus = CoverageStatus.COMPLETE;
        }

        CoverageSummary coverage = new CoverageSummary(
                coverageStatus,
                coverageResult.missingSpeakers(),
                sourcesWithoutSpeakers
        );

        // Associate each coverage flag with an actual source, not a speaker ID.
        Set<String> missingNormalized = coverageResult.missingSpeakers().stream()
                .map(code -> code.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        for (var entry : eligibleSpeakers.entrySet()) {
            String normalized = entry.getValue().trim()
                    .toUpperCase(java.util.Locale.ROOT);
            if (missingNormalized.contains(normalized)) {
                outcomes.add(new PipelineOutcome(
                        entry.getKey(), "exceptions_queue", "REPRESENTATION_GAP",
                        null, null, null, 0
                ));
            }
        }

        List<PipelineOutcome> clean = outcomes.stream().filter(o -> o.stageReached().equals("clean")).toList();
        List<PipelineOutcome> exceptions = outcomes.stream().filter(o -> o.stageReached().equals("exceptions_queue")).toList();
        List<PipelineOutcome> rejected = outcomes.stream().filter(o -> o.stageReached().equals("rejected_at_gate")).toList();

        return new PipelineRun(outcomes, clean, exceptions, rejected, findings, coverage);
    }

    // ---------------------------------------------------------------
    // Demo dataset — synthetic only, per the Runbook's staged approach
    // ---------------------------------------------------------------
    public static List<PipelineItem> demoItems() {
        List<PipelineItem> items = new ArrayList<>();
        items.add(new PipelineItem("WS-001", 1,
                "The workshop was really well organised. I enjoyed the group activities "
                        + "and thought the facilitators explained things clearly.", null));
        items.add(new PipelineItem("WS-002", 2,
                "My name is Sarah Thompson and you can reach me at sarah.t@example.com. "
                        + "I felt the program helped me reconnect with my community.", null));
        items.add(new PipelineItem("WS-003", 2,
                "We met at Riverbend Community Hall and it felt like a really safe space "
                        + "to share what mattered to us.", null));
        items.add(new PipelineItem("WS-004", 3,
                "This session covered material that should not be shared outside the community.", null));
        items.add(new PipelineItem("WS-005", null,
                "Some feedback that was never given a tier before being uploaded.", null));
        Map<String, Object> badMockProfile = new HashMap<>();
        badMockProfile.put("theme", "Session length");
        badMockProfile.put("quote", "this exact phrase is not in the source text");
        badMockProfile.put("confidence", 0.88);
        items.add(new PipelineItem("WS-006", 1,
                "I thought the second session ran a bit long, but overall it was useful "
                        + "and I'd recommend it to others.", badMockProfile));
        items.add(new PipelineItem("WS-007", 2,
                "My aunty said the program helped her feel more connected to the group.", null));
        return items;
    }
}
