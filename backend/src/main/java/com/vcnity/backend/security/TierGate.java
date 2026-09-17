package com.vcnity.backend.security;

import java.util.Map;
import java.util.Set;

/**
 * TierGate.java
 *
 * Enforces the single most important rule in the pipeline design:
 * no item may be transcribed, de-identified, sent to a model, or
 * touched in any automated way unless it has passed this gate first.
 *
 * This fixes the bug the adversarial review flagged: the original
 * design described the Tier 3 check happening partly at intake and
 * partly as a flag inside the grounding stage. A flag at the grounding
 * stage implies the item was ALREADY sent to a model to be coded before
 * anyone noticed it was Tier 3 — which breaches the "never
 * auto-processed" rule by definition, since grounding runs *after*
 * coding.
 *
 * The fix: tierGate() is the literal first call in the pipeline,
 * before transcription, before de-identification, before any API call
 * of any kind. There is exactly one place in the codebase where Tier 3
 * is checked, and it is here.
 */
public final class TierGate {

    private static final int RESTRICTED_TIER = 3;
    private static final Set<Integer> ALL_KNOWN_TIERS = Set.of(1, 2, 3);

    private TierGate() {
        // utility class, not instantiable
    }

    /** Raised when an item may not proceed into the automated pipeline. */
    public static class TierGateException extends RuntimeException {
        private final String itemId;
        private final String reasonText;

        public TierGateException(String itemId, String reason) {
            super("Item '" + itemId + "' rejected at tier gate: " + reason);
            this.itemId = itemId;
            this.reasonText = reason;
        }

        public String getItemId() {
            return itemId;
        }

        public String getReasonText() {
            return reasonText;
        }
    }

    public record TierGateResult(String itemId, int tier, boolean allowed) {
    }

    /**
     * Call this before ANY other pipeline step touches {@code item}.
     *
     * item: expected to contain at least "itemId" (String) and "tier"
     * (Integer, may be null/absent — must be assigned by a human / the
     * community, never inferred automatically).
     *
     * Throws TierGateException if the item may not proceed.
     */
    public static TierGateResult tierGate(Map<String, Object> item) {
        String itemId = item.get("itemId") != null ? item.get("itemId").toString() : "<unknown>";
        Object tierObj = item.get("tier");

        if (tierObj == null) {
            throw new TierGateException(
                    itemId,
                    "no tier assigned. No untiered item may enter the pipeline - "
                            + "tiering must happen, and be recorded, before intake.");
        }

        int tier;
        try {
            tier = (tierObj instanceof Integer) ? (Integer) tierObj : Integer.parseInt(tierObj.toString());
        } catch (NumberFormatException e) {
            throw new TierGateException(itemId, "unrecognised tier value '" + tierObj + "'. Expected one of [1, 2, 3].");
        }

        if (!ALL_KNOWN_TIERS.contains(tier)) {
            throw new TierGateException(itemId, "unrecognised tier value " + tier + ". Expected one of [1, 2, 3].");
        }

        if (tier == RESTRICTED_TIER) {
            throw new TierGateException(
                    itemId,
                    "Tier 3 material is community-controlled and must never be "
                            + "automatically processed. This item requires a human-managed "
                            + "workflow outside this pipeline entirely.");
        }

        return new TierGateResult(itemId, tier, true);
    }
}
