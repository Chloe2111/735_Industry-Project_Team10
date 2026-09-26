package vcnity.pipeline;

import java.util.Set;

/**
 * TierGate.java
 *
 * Enforces the single most important rule in the pipeline design:
 *
 *     No item may be transcribed, de-identified, sent to a model, or
 *     touched in any automated way unless it has passed this gate first.
 *
 * This fixes the bug the adversarial review flagged: the original design
 * described the Tier 3 check happening partly at intake and partly as a
 * flag inside the grounding stage. A flag at the grounding stage implies
 * the item was ALREADY sent to a model to be coded/themed before anyone
 * noticed it was Tier 3 - which breaches the "never auto-processed" rule
 * by definition, since grounding runs *after* coding.
 *
 * The fix: tierGate() is the literal first method call in the pipeline,
 * before transcription, before de-identification, before any API call of
 * any kind. There is exactly one place in the codebase where Tier 3 is
 * checked, and it is here.
 */
public final class TierGate {

    /** Tiers allowed to proceed automatically. */
    public static final Set<Integer> VALID_TIERS = Set.of(1, 2);

    /** Never auto-processed, ever. */
    public static final int RESTRICTED_TIER = 3;

    public static final Set<Integer> ALL_KNOWN_TIERS = Set.of(1, 2, 3);

    private TierGate() {
    }

    /**
     * Thrown when an item may not proceed past the gate. Carries the
     * itemId and a human-readable reason, so callers can route the item
     * to the "rejected at gate" bucket and log why.
     */
    public static final class TierGateException extends RuntimeException {
        public final String itemId;
        public final String reason;

        public TierGateException(String itemId, String reason) {
            super("Item '" + itemId + "' rejected at tier gate: " + reason);
            this.itemId = itemId;
            this.reason = reason;
        }
    }

    /** Returned when an item is allowed to proceed. */
    public record Result(String itemId, int tier, boolean allowed) {
    }

    /**
     * Call this before ANY other pipeline step touches {@code item}.
     *
     * @param item expected to have at least an itemId and a tier (tier
     *             must be assigned by a human / the community, never
     *             inferred automatically)
     * @return a Result if the item may proceed
     * @throws TierGateException if the item may not proceed
     */
    public static Result tierGate(Item item) {
        String itemId = item.itemId() != null ? item.itemId() : "<unknown>";
        Integer tier = item.tier();

        if (tier == null) {
            throw new TierGateException(
                    itemId,
                    "no tier assigned. No untiered item may enter the pipeline - "
                            + "tiering must happen, and be recorded, before intake.");
        }

        if (!ALL_KNOWN_TIERS.contains(tier)) {
            throw new TierGateException(
                    itemId,
                    "unrecognised tier value " + tier + ". Expected one of [1, 2, 3].");
        }

        if (tier == RESTRICTED_TIER) {
            throw new TierGateException(
                    itemId,
                    "Tier 3 material is community-controlled and must never be "
                            + "automatically processed. This item requires a human-managed "
                            + "workflow outside this pipeline entirely.");
        }

        return new Result(itemId, tier, true);
    }
}
