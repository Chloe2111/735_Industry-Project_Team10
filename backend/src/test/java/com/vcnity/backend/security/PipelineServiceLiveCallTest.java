package com.vcnity.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The live Claude path, tested against a local fake server (no real API, no key, no cost).
 * Before this fix the reply was read with a regex that stopped at the first escaped quote,
 * so every live call produced an empty theme and quote and ended as coding_failed.
 */
class PipelineServiceLiveCallTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CODED =
            "{\"theme\": \"Affordable transport\", \"quote\": \"We need affordable transport\", \"confidence\": 0.86}";

    private static String apiReply(String text) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("id", "msg_test");
        root.put("type", "message");
        root.put("role", "assistant");
        ObjectNode block = root.putArray("content").addObject();
        block.put("type", "text");
        block.put("text", text);
        root.put("stop_reason", "end_turn");
        return JSON.writeValueAsString(root);
    }

    @Test
    void parsesANormalReply() throws Exception {
        Map<String, Object> coded = PipelineService.parseCodingResponse(apiReply(CODED));
        assertEquals("Affordable transport", coded.get("theme"));
        assertEquals("We need affordable transport", coded.get("quote"));
        assertEquals(0.86, (Double) coded.get("confidence"), 1e-9);
    }

    @Test
    void toleratesACodeFenceAroundTheJson() throws Exception {
        Map<String, Object> coded = PipelineService.parseCodingResponse(
                apiReply("Here you go:\n```json\n" + CODED + "\n```"));
        assertEquals("Affordable transport", coded.get("theme"));
    }

    @Test
    void handlesQuotesInsideValuesAndANumericString() throws Exception {
        Map<String, Object> coded = PipelineService.parseCodingResponse(
                apiReply("{\"theme\": \"Say \\\"no\\\" to cuts\", \"quote\": \"x\", \"confidence\": \"0.7\"}"));
        assertEquals("Say \"no\" to cuts", coded.get("theme"));
        assertEquals(0.7, (Double) coded.get("confidence"), 1e-9);
    }

    @Test
    void rejectsAReplyWithoutJson() {
        assertThrows(IOException.class,
                () -> PipelineService.parseCodingResponse(apiReply("Sorry, I can't help with that.")));
    }

    @Test
    void requestBodyEscapesTheText() throws Exception {
        String prompt = "line1\n\"quoted\" \\ end";
        String body = PipelineService.buildRequestBody("some-model", prompt);
        assertEquals(prompt, JSON.readTree(body).path("messages").get(0).path("content").asText());
        assertEquals("some-model", JSON.readTree(body).path("model").asText());
    }

    @Test
    void livePipelineCodesDeidentifiedTextAndReportsApiErrors() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        AtomicReference<String> sentKey = new AtomicReference<>();
        AtomicReference<String> sentBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            sentKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String reply = status.get() == 200
                    ? apiReply("{\"theme\": \"Transport access\", \"quote\": \"We need affordable transport to attend community workshops\", \"confidence\": 0.91}")
                    : "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages";
            PipelineService service = new PipelineService("test-key", "test-model", url);
            assertFalse(service.isMockMode());

            PipelineService.PipelineRun run = service.runPipeline(List.of(new PipelineService.PipelineItem(
                    "SUB-1", 2,
                    "We need affordable transport to attend community workshops. Call Tim Nichols on 0412 345 678.",
                    null)), Set.of(), Set.of());

            assertEquals(1, run.findings().size());
            assertEquals("Transport access", run.findings().get(0).theme());
            assertEquals(1, run.clean().size());
            assertEquals("test-key", sentKey.get());
            assertTrue(sentBody.get().contains("\"model\":\"test-model\""));
            assertFalse(sentBody.get().contains("Tim Nichols"), "name must be redacted before the API call");
            assertFalse(sentBody.get().contains("0412 345 678"), "phone must be redacted before the API call");

            status.set(401);
            run = service.runPipeline(List.of(new PipelineService.PipelineItem(
                    "SUB-2", 2, "Some feedback text here.", null)), Set.of(), Set.of());
            assertTrue(run.findings().isEmpty());
            assertTrue(run.outcomes().stream().anyMatch(o -> "coding_failed".equals(o.stageReached())));
        } finally {
            server.stop(0);
        }
    }
}
