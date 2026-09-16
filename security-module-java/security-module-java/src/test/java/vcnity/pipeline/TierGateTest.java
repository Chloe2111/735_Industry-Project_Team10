package vcnity.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TierGateTest {

    @Test
    public void tier1IsAllowed() {
        TierGate.Result result = TierGate.tierGate(new Item("A1", 1, ""));
        assertTrue(result.allowed());
        assertEquals(1, result.tier());
    }

    @Test
    public void tier2IsAllowed() {
        TierGate.Result result = TierGate.tierGate(new Item("A2", 2, ""));
        assertTrue(result.allowed());
    }

    @Test
    public void untieredItemIsRejected() {
        TierGate.TierGateException ex = assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(new Item("B1", null, "")));
        assertTrue(ex.reason.contains("no tier assigned"));
    }

    @Test
    public void tier3ItemIsRejectedBeforeAnyProcessing() {
        TierGate.TierGateException ex = assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(new Item("C1", 3, "")));
        assertTrue(ex.reason.contains("community-controlled"));
        assertTrue(ex.reason.toLowerCase().contains("never"));
    }

    @Test
    public void unrecognisedTierValueIsRejected() {
        assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(new Item("D1", 99, "")));
    }

    @Test
    public void nullTierValueIsRejected() {
        assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(new Item("E1", null, "")));
    }

    @Test
    public void errorCarriesItemIdForLogging() {
        TierGate.TierGateException ex = assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(new Item("F1", 3, "")));
        assertEquals("F1", ex.itemId);
    }
}
