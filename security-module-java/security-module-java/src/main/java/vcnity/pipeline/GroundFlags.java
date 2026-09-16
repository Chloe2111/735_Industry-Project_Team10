package vcnity.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Every check that {@link Grounding#ground} performs on a coded item.
 * Any single true flag routes the item to the exceptions queue for
 * human review.
 */
public record GroundFlags(
        boolean sourceMissing,
        boolean quoteNotGrounded,
        boolean lowConfidence,
        boolean tierViolation,
        boolean contradiction
) {

    public static GroundFlags none() {
        return new GroundFlags(false, false, false, false, false);
    }

    public boolean any() {
        return sourceMissing || quoteNotGrounded || lowConfidence || tierViolation || contradiction;
    }

    /** Names of the failed checks, in a fixed order, comma-separated —
     * used for the "reason" shown in the exceptions queue. */
    public String failedNames() {
        List<String> names = new ArrayList<>();
        if (sourceMissing) names.add("sourceMissing");
        if (quoteNotGrounded) names.add("quoteNotGrounded");
        if (lowConfidence) names.add("lowConfidence");
        if (tierViolation) names.add("tierViolation");
        if (contradiction) names.add("contradiction");
        return String.join(", ", names);
    }
}
