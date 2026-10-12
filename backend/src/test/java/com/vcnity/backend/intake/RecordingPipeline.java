package com.vcnity.backend.intake;

import com.vcnity.backend.security.Deidentify;
import com.vcnity.backend.security.PipelineService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test stand-in for the pipeline. It records every call, so a test can prove the pipeline
 * was (or was not) reached, and returns whatever outcome the test asks for.
 */
class RecordingPipeline implements PipelineRunner {

    record Call(String itemId, int tier, String text) {
    }

    final List<Call> calls = new ArrayList<>();
    /** Every text that was sent for de-identification, so a test can prove Tier 3 text never was. */
    final List<String> deidentifyCalls = new ArrayList<>();
    private RuntimeException deidentifyFailure = null;
    private Runnable whileRunning = null;
    private String stage = "clean";
    private String reason = null;
    private Double confidence = 0.9;
    private Exception failure = null;

    void returnsClean() {
        stage = "clean";
        reason = null;
    }

    void returnsFlagged(String flags) {
        stage = "exceptions_queue";
        reason = flags;
    }

    void returnsStage(String stageReached, String why) {
        stage = stageReached;
        reason = why;
    }

    void returnsConfidence(Double value) {
        confidence = value;
    }

    void failsWith(Exception e) {
        failure = e;
    }

    /** Something that happens while the pipeline is busy, e.g. a coordinator re-tiering the group. */
    void whileRunning(Runnable action) {
        whileRunning = action;
    }

    void deidentifyFailsWith(RuntimeException e) {
        deidentifyFailure = e;
    }

    /** The real de-identification rules (emails, phone numbers, names), with empty term lists. */
    @Override
    public String deidentify(String text) {
        deidentifyCalls.add(text);
        if (deidentifyFailure != null) throw deidentifyFailure;
        return Deidentify.deidentify(text, Set.of(), Set.of()).redactedText();
    }

    @Override
    public PipelineService.PipelineOutcome run(String itemId, int tier, String text) throws Exception {
        calls.add(new Call(itemId, tier, text));
        if (failure != null) throw failure;
        if (whileRunning != null) {
            Runnable action = whileRunning;
            whileRunning = null;
            action.run();
        }
        boolean coded = !"rejected_at_gate".equals(stage);
        return new PipelineService.PipelineOutcome(
                itemId, stage, reason,
                coded ? "General feedback" : null,
                coded ? "a quote from the text" : null,
                coded ? confidence : null,
                0);
    }
}
