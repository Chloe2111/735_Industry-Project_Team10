package vcnity.pipeline;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Grounding.java
 *
 * The deterministic checks that run on every AI-coded item before it can
 * be marked "clean" and skip human review.
 *
 * Fixes the bug the adversarial review flagged: the original quote-match
 * logic stripped ALL whitespace from both the quote and the source before
 * comparing them as substrings. That means a fabricated quote could pass
 * as "grounded" if it matched across word boundaries once whitespace was
 * removed.
 *
 * The fix below normalises whitespace (collapses, doesn't delete it) and
 * matches on real word boundaries, so a quote only counts as grounded if
 * it appears as an actual contiguous sequence of whole words in the
 * source.
 */
public final class Grounding {

    public static final double CONFIDENCE_THRESHOLD = 0.75;

    private Grounding() {
    }

    /** Collapse whitespace and lowercase. Does NOT delete whitespace —
     * that was the original bug. */
    public static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * True only if {@code quote} appears in {@code sourceText} as a
     * contiguous sequence of whole words (word-boundary match), not as
     * an arbitrary substring.
     */
    public static boolean quoteIsGrounded(String quote, String sourceText) {
        if (quote == null || quote.isEmpty() || sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        String q = normalize(quote);
        String s = normalize(sourceText);
        if (q.isEmpty()) {
            return false;
        }

        // Pattern.quote() makes the whole normalised quote literal, while
        // the lookarounds outside it still work as real word boundaries.
        Pattern pattern = Pattern.compile("(?<!\\w)" + Pattern.quote(q) + "(?!\\w)");
        return pattern.matcher(s).find();
    }

    /**
     * codedItem: the model's structured output for one item.
     * sourceLookup: map from sourceRef -> full source text, built from
     *   the corpus BEFORE coding runs (so a missing source is a real
     *   integrity failure, not a lookup-order bug).
     *
     * Returns a GroundResult. isClean is true only if every check
     * passes; any single failed check routes the item to the exceptions
     * queue for human review.
     */
    public static GroundResult ground(CodedItem codedItem, Map<String, String> sourceLookup) {
        String itemId = codedItem.itemId() != null ? codedItem.itemId() : "<unknown>";

        String sourceText = sourceLookup.get(codedItem.sourceRef());

        // Fix for the second bug the review found: SOURCE_MISSING was
        // unreachable because the original code looked the source up
        // AFTER already assuming it existed. Here the existence check
        // happens first and is the only thing gating the rest.
        if (sourceText == null) {
            return new GroundResult(itemId, false, new GroundFlags(true, false, false, false, false));
        }

        boolean quoteNotGrounded = !quoteIsGrounded(
                codedItem.quote() != null ? codedItem.quote() : "", sourceText);
        boolean lowConfidence = codedItem.confidence() < CONFIDENCE_THRESHOLD;
        boolean tierViolation = codedItem.tier() != null && codedItem.tier() == 3;

        // Contradiction scan is intentionally a hook, not a full NLP
        // model, for this MVP — wire in a real check before the real
        // pilot.
        boolean contradiction = codedItem.contradictsPriorTheme();

        GroundFlags flags = new GroundFlags(false, quoteNotGrounded, lowConfidence, tierViolation, contradiction);
        return new GroundResult(itemId, !flags.any(), flags);
    }
}
