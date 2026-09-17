package com.vcnity.backend.security;

import java.util.List;
import java.util.Set;

/**
 * PipelineDemo.java
 *
 * Standalone runner to verify PipelineService end-to-end, mirroring
 * pipelineDemo.js in the Node version. Not a Spring component — just a
 * main() for quick verification. Run with:
 *   java -cp target/classes com.vcnity.backend.security.PipelineDemo
 */
public class PipelineDemo {
    public static void main(String[] args) throws Exception {
        PipelineService service = new PipelineService();

        System.out.println();
        System.out.println("=".repeat(72));
        System.out.println("  VCNITY PIPELINE DEMO (Java) - SYNTHETIC DATA ONLY");
        System.out.println("  Mode: " + (service.isMockMode() ? "MOCK (no ANTHROPIC_API_KEY set)" : "LIVE Claude API"));
        System.out.println("=".repeat(72));
        System.out.println();

        List<PipelineService.PipelineItem> items = PipelineService.demoItems();
        System.out.println("Processing " + items.size() + " synthetic items through the pipeline:\n");
        for (PipelineService.PipelineItem item : items) {
            String tierDisplay = item.tier() != null ? item.tier().toString() : "UNTIERED";
            System.out.println("  " + item.itemId() + "  (tier: " + tierDisplay + ")");
        }
        System.out.println();

        Set<String> gazetteer = Deidentify.loadTermListFromClasspath("communityGazetteer.txt");
        Set<String> vernacularTerms = Deidentify.loadTermListFromClasspath("vernacularTerms.txt");

        PipelineService.PipelineRun run = service.runPipeline(items, gazetteer, vernacularTerms);

        System.out.println("-".repeat(72));
        System.out.println("REJECTED AT TIER GATE");
        System.out.println("-".repeat(72));
        if (run.rejected().isEmpty()) System.out.println("  (none)");
        for (var o : run.rejected()) {
            System.out.println("  " + o.itemId() + ": " + o.reason());
        }
        System.out.println();

        System.out.println("-".repeat(72));
        System.out.println("ROUTED TO EXCEPTIONS QUEUE");
        System.out.println("-".repeat(72));
        if (run.exceptions().isEmpty()) System.out.println("  (none)");
        for (var o : run.exceptions()) {
            System.out.println("  " + o.itemId() + ": failed check(s) -> " + o.reason());
            System.out.println("      theme: \"" + o.theme() + "\"  quote: \"" + o.quote() + "\"  confidence: " + o.confidence());
            System.out.println("      redactions applied before coding: " + o.redactionCount());
        }
        System.out.println();

        System.out.println("-".repeat(72));
        System.out.println("CLEAN OUTPUT");
        System.out.println("-".repeat(72));
        if (run.clean().isEmpty()) System.out.println("  (none)");
        for (var o : run.clean()) {
            System.out.println("  " + o.itemId() + ": theme=\"" + o.theme() + "\"");
            System.out.println("      quote: \"" + o.quote() + "\"  confidence: " + o.confidence());
            System.out.println("      redactions applied before coding: " + o.redactionCount());
        }
        System.out.println();

        System.out.println("=".repeat(72));
        System.out.println("  SUMMARY: " + run.clean().size() + " clean, " + run.exceptions().size()
                + " to exceptions queue, " + run.rejected().size() + " rejected at tier gate");
        System.out.println("=".repeat(72));
        System.out.println();
    }
}
