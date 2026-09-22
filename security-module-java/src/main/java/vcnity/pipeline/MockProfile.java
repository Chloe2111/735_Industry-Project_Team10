package vcnity.pipeline;

/**
 * Forces a specific coded output when running in mock mode, so a demo
 * dataset can deliberately include a "bad" AI output and show that the
 * grounding checks actually catch it.
 */
public record MockProfile(String theme, String quote, double confidence) {
}
