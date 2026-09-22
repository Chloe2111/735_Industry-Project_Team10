package vcnity.pipeline;

/**
 * The model's structured output for one item, ready for grounding
 * checks: itemId, sourceRef, quote, confidence, tier.
 */
public record CodedItem(
        String itemId,
        String sourceRef,
        String quote,
        double confidence,
        Integer tier,
        boolean contradictsPriorTheme
) {

    public CodedItem(String itemId, String sourceRef, String quote, double confidence, Integer tier) {
        this(itemId, sourceRef, quote, confidence, tier, false);
    }
}
