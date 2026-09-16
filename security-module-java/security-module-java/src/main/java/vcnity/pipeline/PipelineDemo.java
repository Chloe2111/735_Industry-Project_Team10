package vcnity.pipeline;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PipelineDemo.java
 *
 * Run this for a live, narrated walkthrough of the pipeline.
 *
 *     mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.PipelineDemo
 *
 * Runs entirely on synthetic data. Uses the real Claude API if
 * ANTHROPIC_API_KEY is set in your environment, otherwise runs in mock
 * mode — clearly labelled either way.
 */
public final class PipelineDemo {

    private PipelineDemo() {
    }

    private static void line() {
        line('-', 72);
    }

    private static void line(char ch, int width) {
        System.out.println(String.valueOf(ch).repeat(width));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Force UTF-8 output regardless of the host's default console
        // encoding, so the — and other punctuation below always render
        // correctly instead of turning into '?'.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        System.out.println();
        line('=', 72);
        System.out.println("  VCNITY PIPELINE DEMO \u2014 SYNTHETIC DATA ONLY");
        System.out.println("  Mode: " + (PipelineCore.MOCK_MODE ? "MOCK (no ANTHROPIC_API_KEY set)" : "LIVE Claude API"));
        line('=', 72);
        System.out.println();

        List<Item> items = PipelineCore.demoItems();
        System.out.println("Processing " + items.size() + " synthetic items through the pipeline:\n");
        for (Item item : items) {
            String tierDisplay = item.tier() != null ? String.valueOf(item.tier()) : "UNTIERED";
            System.out.println("  " + item.itemId() + "  (tier: " + tierDisplay + ")");
        }
        System.out.println();

        PipelineResult run = PipelineCore.runPipeline(items);

        line();
        System.out.println("REJECTED AT TIER GATE (never touched transcription, de-id, or the model)");
        line();
        if (run.rejected().isEmpty()) {
            System.out.println("  (none)");
        }
        for (Outcome o : run.rejected()) {
            System.out.println("  " + o.itemId() + ": " + o.reason());
        }
        System.out.println();

        line();
        System.out.println("ROUTED TO EXCEPTIONS QUEUE (needs human review before it can ship)");
        line();
        if (run.exceptions().isEmpty()) {
            System.out.println("  (none)");
        }
        for (Outcome o : run.exceptions()) {
            System.out.println("  " + o.itemId() + ": failed check(s) -> " + o.reason());
            System.out.println("      theme: \"" + o.theme() + "\"  quote: \"" + o.quote()
                    + "\"  confidence: " + o.confidence());
            System.out.println("      redactions applied before coding: " + o.redactionCount());
        }
        System.out.println();

        line();
        System.out.println("CLEAN OUTPUT (passed every check, ready for the report)");
        line();
        if (run.clean().isEmpty()) {
            System.out.println("  (none)");
        }
        for (Outcome o : run.clean()) {
            System.out.println("  " + o.itemId() + ": theme=\"" + o.theme() + "\"");
            System.out.println("      quote: \"" + o.quote() + "\"  confidence: " + o.confidence());
            System.out.println("      redactions applied before coding: " + o.redactionCount());
        }
        System.out.println();

        line('=', 72);
        System.out.println("  SUMMARY: " + run.clean().size() + " clean, " + run.exceptions().size()
                + " to exceptions queue, " + run.rejected().size() + " rejected at tier gate");
        line('=', 72);
        System.out.println();
    }
}
