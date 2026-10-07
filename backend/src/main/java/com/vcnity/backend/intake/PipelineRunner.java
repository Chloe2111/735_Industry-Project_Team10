package com.vcnity.backend.intake;

import com.vcnity.backend.security.PipelineService;

/**
 * Runs one piece of text through the pipeline (tier gate, de-identification, coding, grounding).
 * A small interface so tests can prove when the pipeline is, and is not, called.
 */
public interface PipelineRunner {

    PipelineService.PipelineOutcome run(String itemId, int tier, String text) throws Exception;

    /**
     * The same text with names, contact details and listed terms replaced by [REDACTED:...] tags:
     * exactly what the pipeline's own de-identification step produces. This is the only version of
     * a Tier 1 or 2 submission that is stored or published.
     */
    String deidentify(String text);
}
