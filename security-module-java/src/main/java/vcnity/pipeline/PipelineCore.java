package vcnity.pipeline;

import vcnity.pipeline.json.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PipelineCore.java
 *
 * Wires the three guardrail modules (TierGate, Deidentify, Grounding)
 * into one end-to-end pipeline: intake -> tier check -> de-identify ->
 * AI coding -> grounding check -> clean output / exceptions queue.
 *
 * Uses the real Claude API if ANTHROPIC_API_KEY is set in the
 * environment. Otherwise falls back to a clearly-labeled mock coder so
 * the demo runs out of the box without a key.
 *
 * Note: the live-API path talks to Claude with a hand-rolled HTTP call
 * (java.net.http.HttpClient) plus the tiny JsonUtil helper, on purpose -
 * this keeps the guardrail module dependency-free. If you're already
 * using an official Anthropic Java SDK elsewhere in your codebase, feel
 * free to swap callClaude() to use it instead; nothing else here needs
 * to change.
 */
public final class PipelineCore {

    public static final boolean MOCK_MODE = isMockMode();

    private static final String CLAUDE_MODEL = "claude-sonnet-4-6";
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private PipelineCore() {
    }

    private static boolean isMockMode() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key == null || key.isBlank();
    }

    // ---------------------------------------------------------------------
    // Transcription stub
    // ---------------------------------------------------------------------
    // All demo items are already text. In the real pipeline, audio/video
    // items would be transcribed by Whisper here, AFTER tierGate() and
    // BEFORE de-identification.

    private static String transcribe(Item item) {
        return item.rawText();
    }

    // ---------------------------------------------------------------------
    // AI coding step - real Claude API if a key is available, else mock
    // ---------------------------------------------------------------------

    private static String codingPrompt(String text) {
        return "You are coding a single piece of workshop feedback for a thematic "
                + "analysis pipeline. Given the text below, return ONLY a JSON object (no other text) with:\n"
                + "  - \"theme\": a short theme label (a few words)\n"
                + "  - \"quote\": a verbatim short quote from the text below that supports the theme "
                + "(must be an exact substring of the text, not paraphrased)\n"
                + "  - \"confidence\": a number between 0 and 1\n\n"
                + "TEXT:\n"
                + "\"\"\"" + text + "\"\"\"\n\n"
                + "Return ONLY the JSON object.\n";
    }

    static CodedOutput mockCode(String text, MockProfile mockProfile) {
        if (mockProfile != null) {
            return new CodedOutput(mockProfile.theme(), mockProfile.quote(), mockProfile.confidence());
        }
        String firstSentence = text.split("\\.")[0].trim() + ".";
        return new CodedOutput("General feedback", firstSentence, 0.9);
    }

    static CodedOutput codeItem(String text, MockProfile mockProfile) throws IOException, InterruptedException {
        if (MOCK_MODE) {
            return mockCode(text, mockProfile);
        }
        return callClaude(text);
    }

    private static CodedOutput callClaude(String text) throws IOException, InterruptedException {
        String prompt = codingPrompt(text);
        String body = "{"
                + "\"model\":" + JsonUtil.quote(CLAUDE_MODEL) + ","
                + "\"max_tokens\":300,"
                + "\"messages\":[{\"role\":\"user\",\"content\":" + JsonUtil.quote(prompt) + "}]"
                + "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        try {
            Map<String, Object> parsed = JsonUtil.parseObject(response.body());
            Object contentObj = parsed.get("content");
            if (!(contentObj instanceof List<?> contentList) || contentList.isEmpty()) {
                return new CodedOutput("unparseable", "", 0.0);
            }
            Object first = contentList.get(0);
            if (!(first instanceof Map<?, ?> firstBlock)) {
                return new CodedOutput("unparseable", "", 0.0);
            }
            Object rawText = firstBlock.get("text");
            if (!(rawText instanceof String rawTextStr)) {
                return new CodedOutput("unparseable", "", 0.0);
            }

            Map<String, Object> coded = JsonUtil.parseObject(rawTextStr.trim());
            String theme = String.valueOf(coded.getOrDefault("theme", "unparseable"));
            String quote = String.valueOf(coded.getOrDefault("quote", ""));
            double confidence = coded.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
            return new CodedOutput(theme, quote, confidence);
        } catch (Exception e) {
            // If the model didn't return clean JSON, treat it as a
            // low-confidence result rather than crashing the pipeline.
            return new CodedOutput("unparseable", "", 0.0);
        }
    }

    // ---------------------------------------------------------------------
    // Pipeline orchestration
    // ---------------------------------------------------------------------

    public static PipelineResult runPipeline(List<Item> items) throws IOException, InterruptedException {
        return runPipeline(items, "communityGazetteer.txt");
    }

    public static PipelineResult runPipeline(List<Item> items, String gazetteerPath)
            throws IOException, InterruptedException {
        Set<String> gazetteer = Deidentify.loadGazetteer(gazetteerPath);
        List<Outcome> outcomes = new ArrayList<>();

        for (Item item : items) {
            String itemId = item.itemId() != null ? item.itemId() : "<unknown>";

            // Stage 1: tier gate - the ONLY place Tier 3 / untiered items
            // are checked.
            try {
                TierGate.tierGate(item);
            } catch (TierGate.TierGateException e) {
                outcomes.add(Outcome.rejected(itemId, e.reason));
                continue;
            }

            // Stage 2: transcription (stub for text items)
            String rawText = transcribe(item);

            // Stage 3: de-identification
            Deidentify.Result deidResult = Deidentify.deidentify(rawText, gazetteer);
            String cleanText = deidResult.redactedText();

            // Stage 4: AI coding (real Claude API or mock)
            CodedOutput coded = codeItem(cleanText, item.mockProfile());
            CodedItem codedItem = new CodedItem(itemId, itemId, coded.quote(), coded.confidence(), item.tier());
            Map<String, String> sourceLookup = Map.of(itemId, cleanText);

            // Stage 5: grounding checks
            GroundResult result = Grounding.ground(codedItem, sourceLookup);

            if (result.isClean()) {
                outcomes.add(Outcome.clean(
                        itemId, coded.theme(), coded.quote(), coded.confidence(), deidResult.entityCount()));
            } else {
                outcomes.add(Outcome.exceptions(
                        itemId, result.flags().failedNames(), coded.theme(), coded.quote(),
                        coded.confidence(), deidResult.entityCount()));
            }
        }

        return new PipelineResult(outcomes);
    }

    // ---------------------------------------------------------------------
    // Demo dataset - synthetic only, per the Runbook's staged approach
    // ---------------------------------------------------------------------

    public static List<Item> demoItems() {
        return List.of(
                new Item("WS-001", 1,
                        "The workshop was really well organised. I enjoyed the group activities "
                                + "and thought the facilitators explained things clearly."),
                new Item("WS-002", 2,
                        "My name is Sarah Thompson and you can reach me at sarah.t@example.com. "
                                + "I felt the program helped me reconnect with my community."),
                new Item("WS-003", 2,
                        "We met at Riverbend Community Hall and it felt like a really safe space "
                                + "to share what mattered to us."),
                new Item("WS-004", 3,
                        "This session covered material that should not be shared outside the community."),
                new Item("WS-005", null,
                        "Some feedback that was never given a tier before being uploaded."),
                new Item("WS-006", 1,
                        "I thought the second session ran a bit long, but overall it was useful "
                                + "and I'd recommend it to others.",
                        // Forces a bad/fabricated output in mock mode, to
                        // demonstrate the exceptions queue catching
                        // something real.
                        new MockProfile("Session length", "this exact phrase is not in the source text", 0.88))
        );
    }
}
