package com.vcnity.backend.intake;

import com.vcnity.backend.security.Deidentify;
import com.vcnity.backend.security.PipelineService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Runs submissions through the existing {@link PipelineService}.
 * With no ANTHROPIC_API_KEY set, PipelineService uses its built-in mock coder.
 */
@Component
public class DefaultPipelineRunner implements PipelineRunner {

    private final PipelineService pipeline = new PipelineService();
    private final Set<String> gazetteer = Deidentify.loadTermListFromClasspath("communityGazetteer.txt");
    private final Set<String> vernacularTerms = Deidentify.loadTermListFromClasspath("vernacularTerms.txt");

    /** True when no API key is configured and the built-in mock coder is used. */
    public boolean isMockMode() {
        return pipeline.isMockMode();
    }

    @Override
    public PipelineService.PipelineOutcome run(String itemId, int tier, String text) throws Exception {
        PipelineService.PipelineItem item = new PipelineService.PipelineItem(itemId, tier, text, null);
        PipelineService.PipelineRun run = pipeline.runPipeline(List.of(item), gazetteer, vernacularTerms);
        return run.outcomes().stream()
                .filter(outcome -> itemId.equals(outcome.itemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The pipeline returned no result for " + itemId));
    }

    /** Same call and same term lists as the de-identification step inside PipelineService. */
    @Override
    public String deidentify(String text) {
        return Deidentify.deidentify(text, gazetteer, vernacularTerms).redactedText();
    }
}
