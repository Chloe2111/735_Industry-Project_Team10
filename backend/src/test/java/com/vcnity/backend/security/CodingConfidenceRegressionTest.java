package com.vcnity.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodingConfidenceRegressionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLEAR =
            "Lower bus fares would help us attend community activities.";

    private PipelineService.PipelineRun runWithReply(
            String source, String theme, String quote, double confidence)
            throws Exception {

        var coded = JSON.createObjectNode();
        coded.put("theme", theme);
        coded.put("quote", quote);
        coded.put("confidence", confidence);

        var response = JSON.createObjectNode();
        var block = response.putArray("content").addObject();
        block.put("type", "text");
        block.put("text", JSON.writeValueAsString(coded));

        byte[] reply = JSON.writeValueAsBytes(response);
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/v1/messages", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });

        server.start();
        try {
            String url = "http://127.0.0.1:"
                    + server.getAddress().getPort() + "/v1/messages";
            var service = new PipelineService(
                    "synthetic-test-key", "test-model", url);

            assertFalse(service.isMockMode());

            return service.runPipeline(
                    List.of(new PipelineService.PipelineItem(
                            "CONFIDENCE-TEST", 2, source, null)),
                    Set.of(), Set.of());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noIdeaResponseProducesNoPublishableFinding() throws Exception {
        var run = runWithReply(
                "idk, things are fine I guess", "", "", 0.0);

        assertTrue(run.clean().isEmpty());
        assertTrue(run.findings().isEmpty());
        assertEquals(1, run.outcomes().size());
        assertEquals("coding_failed",
                run.outcomes().get(0).stageReached());
        assertEquals("INVALID_CODER_OUTPUT",
                run.outcomes().get(0).reason());
    }

    @Test
    void groundedQuoteBelowThresholdRequiresReview() throws Exception {
        var run = runWithReply(
                CLEAR, "Affordable transport", CLEAR, 0.74);

        assertTrue(run.clean().isEmpty());
        assertEquals(1, run.exceptions().size());
        assertEquals(1, run.findings().size());
        assertTrue(run.findings().get(0).flags()
                .contains("lowConfidence"));
        assertFalse(run.findings().get(0).flags()
                .contains("quoteNotGrounded"));
    }

    @Test
    void groundedQuoteAtOrAboveThresholdPassesChecks() throws Exception {
        for (double score : new double[]{0.75, 0.90}) {
            var run = runWithReply(
                    CLEAR, "Affordable transport", CLEAR, score);

            assertEquals(1, run.clean().size(),
                    "Expected clean result for confidence " + score);
            assertTrue(run.exceptions().isEmpty());
            assertEquals(1, run.findings().size());
            assertTrue(run.findings().get(0).flags().isEmpty());
            assertEquals(score,
                    run.findings().get(0).confidence(), 1e-9);
        }
    }
}
