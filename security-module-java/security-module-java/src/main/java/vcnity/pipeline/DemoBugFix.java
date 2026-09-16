package vcnity.pipeline;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * DemoBugFix.java
 *
 * Run this to see, live, the exact bug the adversarial review found in
 * the quote-matching logic, and confirm the fix closes it.
 *
 *     mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.DemoBugFix
 */
public final class DemoBugFix {

    private DemoBugFix() {
    }

    /** This is what the original design actually did: strip ALL
     * whitespace from both strings, then check if one is a substring of
     * the other. */
    private static boolean oldBuggyQuoteIsGrounded(String quote, String sourceText) {
        String q = quote.replaceAll("\\s+", "").toLowerCase();
        String s = sourceText.replaceAll("\\s+", "").toLowerCase();
        return s.contains(q);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String source = "I felt that a really great sense of belonging came from it.";
        String fabricatedQuote = "a tarea"; // does not appear as real words anywhere
        // in the source — but "thatareallygreat" (whitespace stripped)
        // contains "atarea" as a substring, so the old logic is fooled.

        System.out.println("SOURCE TEXT:");
        System.out.println("  \"" + source + "\"\n");
        System.out.println("QUOTE THE MODEL CLAIMS IS IN THE SOURCE:");
        System.out.println("  \"" + fabricatedQuote + "\"\n");

        boolean oldResult = oldBuggyQuoteIsGrounded(fabricatedQuote, source);
        boolean newResult = Grounding.quoteIsGrounded(fabricatedQuote, source);

        System.out.println("Old logic (whitespace-stripped substring match): grounded = " + oldResult);
        System.out.println("Fixed logic (word-boundary match):                grounded = " + newResult + "\n");

        if (oldResult && !newResult) {
            System.out.println("CONFIRMED: the old logic would have let this fabricated quote");
            System.out.println("through as 'clean'. The fixed logic correctly routes it to the");
            System.out.println("exceptions queue for human review instead.");
        } else {
            System.out.println("Unexpected result \u2014 check the demo inputs.");
        }
    }
}
