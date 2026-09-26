package vcnity.pipeline;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Server.java
 *
 * The clickable-link version of the same pipeline demo. Uses the exact
 * same PipelineCore.java as PipelineDemo.java, so the CLI walkthrough
 * and this app can never show different behaviour.
 *
 * Built on the JDK's own com.sun.net.httpserver, so this runs with zero
 * extra dependencies - no Spring Boot needed for a one-page demo.
 *
 * Run locally:
 *     mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.Server
 * then open http://localhost:3000
 *
 * To use the real Claude API instead of mock mode, set
 * ANTHROPIC_API_KEY in your environment before starting the server.
 */
public final class Server {

    private Server() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respondHtml(exchange, renderPage(PipelineCore.demoItems(), null));
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/run", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            List<Item> items = PipelineCore.demoItems();
            try {
                PipelineResult run = PipelineCore.runPipeline(items);
                respondHtml(exchange, renderPage(items, run));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(500, -1);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("VCNITY pipeline demo running at http://localhost:" + port);
    }

    private static void respondHtml(com.sun.net.httpserver.HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String renderPage(List<Item> items, PipelineResult run) {
        String modeLabel = PipelineCore.MOCK_MODE
                ? "Running in <strong>mock mode</strong> - no ANTHROPIC_API_KEY set. AI coding output is simulated."
                : "Running on the <strong>live Claude API</strong>.";

        StringBuilder itemCards = new StringBuilder();
        for (Item item : items) {
            String tierDisplay = item.tier() != null ? "Tier " + item.tier() : "UNTIERED";
            itemCards.append("<div class=\"chip\">").append(item.itemId())
                    .append("<br><span class=\"muted\">").append(tierDisplay)
                    .append("</span></div>");
        }

        String resultsHtml = "";
        if (run != null) {
            StringBuilder rejectedHtml = new StringBuilder();
            for (Outcome o : run.rejected()) {
                rejectedHtml.append("<div class=\"card\"><strong>").append(o.itemId())
                        .append("</strong><p>").append(o.reason()).append("</p></div>");
            }
            resultsHtml = "<div class=\"results\">"
                    + "<div class=\"col\"><h3>\u2705 Clean (" + run.clean().size() + ")</h3>"
                    + (run.clean().isEmpty() ? "<p class='muted'>(none)</p>" : cardsHtml(run.clean()))
                    + "</div>"
                    + "<div class=\"col\"><h3>\u26a0\ufe0f Exceptions queue (" + run.exceptions().size() + ")</h3>"
                    + (run.exceptions().isEmpty() ? "<p class='muted'>(none)</p>" : cardsHtml(run.exceptions()))
                    + "</div>"
                    + "<div class=\"col\"><h3>\u26d4 Rejected at gate (" + run.rejected().size() + ")</h3>"
                    + (run.rejected().isEmpty() ? "<p class='muted'>(none)</p>" : rejectedHtml)
                    + "</div>"
                    + "</div>";
        }

        return "<!DOCTYPE html>\n"
                + "<html>\n<head>\n  <meta charset=\"utf-8\">\n  <title>VCNITY Pipeline Demo</title>\n"
                + "  <style>\n"
                + "    body { font-family: -apple-system, Arial, sans-serif; max-width: 1100px; margin: 40px auto; padding: 0 20px; color: #1a1a1a; }\n"
                + "    h1 { margin-bottom: 4px; }\n"
                + "    .muted { color: #666; font-size: 0.9em; }\n"
                + "    .banner { background: #eef6fb; border: 1px solid #c6e3f0; padding: 10px 14px; border-radius: 8px; margin: 16px 0; }\n"
                + "    .chips { display: flex; gap: 10px; flex-wrap: wrap; margin: 16px 0 24px; }\n"
                + "    .chip { background: #f5f5f5; border-radius: 8px; padding: 10px 16px; text-align: center; min-width: 90px; }\n"
                + "    button { background: #0e2841; color: white; border: none; padding: 10px 22px; font-size: 1em; border-radius: 6px; cursor: pointer; }\n"
                + "    button:hover { background: #16405f; }\n"
                + "    .results { display: flex; gap: 20px; margin-top: 24px; }\n"
                + "    .col { flex: 1; }\n"
                + "    .card { background: #f5f5f5; border-radius: 8px; padding: 12px 14px; margin-bottom: 10px; }\n"
                + "    .card p { margin: 4px 0; font-size: 0.92em; }\n"
                + "  </style>\n</head>\n<body>\n"
                + "  <h1>\ud83d\udd12 VCNITY Pipeline Demo</h1>\n"
                + "  <p class=\"muted\">Synthetic data only - per the project's own governance rule: no real data until privacy officer + HREC sign-off.</p>\n"
                + "  <div class=\"banner\">" + modeLabel + "</div>\n\n"
                + "  <h3>Items entering the pipeline</h3>\n"
                + "  <div class=\"chips\">" + itemCards + "</div>\n\n"
                + "  <form method=\"POST\" action=\"/run\">\n    <button type=\"submit\">Run pipeline</button>\n  </form>\n\n"
                + "  " + resultsHtml + "\n</body>\n</html>";
    }

    private static String cardsHtml(List<Outcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        for (Outcome o : outcomes) {
            sb.append("<div class=\"card\">\n    <strong>").append(o.itemId())
                    .append("</strong> - ").append(o.theme() != null ? o.theme() : "").append("\n    ")
                    .append(o.reason() != null ? "<p><em>failed: " + o.reason() + "</em></p>" : "")
                    .append("\n    <p>Quote: \u201c").append(o.quote() != null ? o.quote() : "")
                    .append("\u201d</p>\n    <p>Confidence: ").append(o.confidence())
                    .append(" | Redactions: ").append(o.redactionCount()).append("</p>\n  </div>");
        }
        return sb.toString();
    }
}
