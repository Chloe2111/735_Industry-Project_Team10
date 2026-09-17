package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TierGateTest {

    private Map<String, Object> item(String itemId, Object tier) {
        Map<String, Object> m = new HashMap<>();
        m.put("itemId", itemId);
        m.put("tier", tier);
        return m;
    }

    @Test
    void tier1IsAllowed() {
        TierGate.TierGateResult result = TierGate.tierGate(item("A1", 1));
        assertTrue(result.allowed());
        assertEquals(1, result.tier());
    }

    @Test
    void tier2IsAllowed() {
        TierGate.TierGateResult result = TierGate.tierGate(item("A2", 2));
        assertTrue(result.allowed());
    }

    @Test
    void untieredItemIsRejected() {
        assertThrows(TierGate.TierGateException.class, () -> TierGate.tierGate(item("B1", null)));
    }

    @Test
    void tier3ItemIsRejectedBeforeAnyProcessing() {
        TierGate.TierGateException e = assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(item("C1", 3)));
        assertTrue(e.getReasonText().contains("community-controlled"));
        assertTrue(e.getReasonText().toLowerCase().contains("never"));
    }

    @Test
    void unrecognisedTierValueIsRejected() {
        assertThrows(TierGate.TierGateException.class, () -> TierGate.tierGate(item("D1", 99)));
    }

    @Test
    void errorCarriesItemIdForLogging() {
        TierGate.TierGateException e = assertThrows(TierGate.TierGateException.class,
                () -> TierGate.tierGate(item("F1", 3)));
        assertEquals("F1", e.getItemId());
    }
}