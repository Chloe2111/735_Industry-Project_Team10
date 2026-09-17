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
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PipelineService() {
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        this.mockMode = (apiKey == null || apiKey.isBlank());
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public record PipelineItem(
            String itemId, Integer tier, String rawText, Map<String, Object> mockProfile) {
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
            List<PipelineOutcome> rejected) {
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

            // Stage 2: transcription (stub for text items)
            String rawText = transcribe(item);

            // Stage 3: de-identification — passes BOTH term lists,
            // including the new vernacular list (DEF-1 fix)
            Deidentify.DeidentificationResult deidResult = Deidentify.deidentify(rawText, gazetteer, vernacularTerms);
            String cleanText = deidResult.redactedText();

            // Stage 4: AI coding (real Claude API or mock)
            Map<String, Object> coded = codeItem(cleanText, item.mockProfile());

            Map<String, Object> codedItem = new HashMap<>();
            codedItem.put("itemId", itemId);
            codedItem.put("sourceRef", itemId);
            codedItem.put("quote", coded.getOrDefault("quote", ""));
            codedItem.put("confidence", coded.getOrDefault("confidence", 0.0));
            codedItem.put("tier", item.tier());

            Map<String, String> sourceLookup = new HashMap<>();
            sourceLookup.put(itemId, cleanText);

            // Stage 5: grounding checks
            Grounding.GroundingResult result = Grounding.ground(codedItem, sourceLookup);

            String theme = (String) coded.get("theme");
            String quote = (String) coded.get("quote");
            Double confidence = coded.get("confidence") != null ? ((Number) coded.get("confidence")).doubleValue() : null;

            if (result.isClean()) {
                outcomes.add(new PipelineOutcome(itemId, "clean", null, theme, quote, confidence, deidResult.entityCount()));
            } else {
                Map<String, Boolean> flagMap = result.flags().asMap();
                StringBuilder failedChecks = new StringBuilder();
                for (Map.Entry<String, Boolean> entry : flagMap.entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue())) {
                        if (failedChecks.length() > 0) failedChecks.append(", ");
                        failedChecks.append(entry.getKey());
                    }
                }
                outcomes.add(new PipelineOutcome(
                        itemId, "exceptions_queue", failedChecks.toString(), theme, quote, confidence, deidResult.entityCount()));
            }
        }

        List<PipelineOutcome> clean = outcomes.stream().filter(o -> o.stageReached().equals("clean")).toList();
        List<PipelineOutcome> exceptions = outcomes.stream().filter(o -> o.stageReached().equals("exceptions_queue")).toList();
        List<PipelineOutcome> rejected = outcomes.stream().filter(o -> o.stageReached().equals("rejected_at_gate")).toList();

        return new PipelineRun(outcomes, clean, exceptions, rejected);
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
