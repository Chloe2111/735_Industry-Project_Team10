package vcnity.pipeline;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * PipelineCoreTest.java
 *
 * TierGateTest proves the gate's decision logic is correct in isolation.
 * This test proves the actual story acceptance criteria end-to-end:
 * that TierGate is really wired in as the FIRST step of the real
 * pipeline, so a Tier 3 or untiered item never reaches transcription,
 * de-identification, or the AI coding step at all — not just that the
 * gate itself works correctly if something remembers to call it.
 *
 * This is the test that would fail if a future refactor accidentally
 * moved the tier check to run after coding, even though every
 * TierGateTest would still pass.
 */
public class PipelineCoreTest {

    private static final String NO_GAZETTEER = "nonexistent-gazetteer.txt";

    @Test
    public void tier3ItemNeverReachesCodingStage() throws IOException, InterruptedException {
        Item restricted = new Item("T3-1", 3, "This should never be seen by any model.");

        PipelineResult result = PipelineCore.runPipeline(List.of(restricted), NO_GAZETTEER);

        assertEquals(1, result.rejected().size());
        assertEquals(0, result.clean().size());
        assertEquals(0, result.exceptions().size());

        Outcome outcome = result.rejected().get(0);
        assertEquals("T3-1", outcome.itemId());
        assertTrue(outcome.reason().contains("community-controlled"));
    }

    @Test
    public void untieredItemNeverReachesCodingStage() throws IOException, InterruptedException {
        Item untiered = new Item("U-1", null, "No tier was ever assigned to this one.");

        PipelineResult result = PipelineCore.runPipeline(List.of(untiered), NO_GAZETTEER);

        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().get(0).reason().contains("no tier assigned"));
    }

    @Test
    public void mixedBatchOnlyRejectsTier3AndUntiered() throws IOException, InterruptedException {
        List<Item> batch = List.of(
                new Item("OK-1", 1, "This is fine feedback with no issues at all."),
                new Item("OK-2", 2, "Another clean piece of feedback here."),
                new Item("T3-2", 3, "Restricted material."),
                new Item("U-2", null, "Missing a tier entirely.")
        );

        PipelineResult result = PipelineCore.runPipeline(batch, NO_GAZETTEER);

        assertEquals(2, result.rejected().size());
        List<String> rejectedIds = result.rejected().stream().map(Outcome::itemId).toList();
        assertTrue(rejectedIds.contains("T3-2"));
        assertTrue(rejectedIds.contains("U-2"));

        // The two valid items should have proceeded far enough to be coded —
        // proving the gate blocks the right items without blocking
        // everything indiscriminately.
        long processedCount = result.clean().size() + result.exceptions().size();
        assertEquals(2, processedCount);
    }

    @Test
    public void rejectedItemsCarryNoCodedOutput() throws IOException, InterruptedException {
        // Guards specifically against a future refactor that moves the tier
        // check to AFTER coding — if that happened, a "rejected" item might
        // still end up with a theme/quote attached from the model, which
        // this test would catch immediately.
        Item restricted = new Item("T3-3", 3, "Restricted material.");

        PipelineResult result = PipelineCore.runPipeline(List.of(restricted), NO_GAZETTEER);

        Outcome outcome = result.rejected().get(0);
        assertNull(outcome.theme());
        assertNull(outcome.quote());
        assertNull(outcome.confidence());
        assertNull(outcome.redactionCount());
    }
}
