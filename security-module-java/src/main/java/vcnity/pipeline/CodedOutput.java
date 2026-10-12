package vcnity.pipeline;

/** What the AI coding step (real Claude API or mock) produces for one item. */
public record CodedOutput(String theme, String quote, double confidence) {
}
