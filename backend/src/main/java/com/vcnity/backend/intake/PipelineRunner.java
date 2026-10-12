package com.vcnity.backend.intake;

import com.vcnity.backend.security.PipelineService;

/**
 * Runs one piece of text through the pipeline (tier gate, de-identification, coding, grounding).
 * A small interface so tests can prove when the pipeline is, and is not, called.
 */
@FunctionalInterface
public interface PipelineRunner {

    PipelineService.PipelineOutcome run(String itemId, int tier, String text) throws Exception;
}
