package vcnity.pipeline;

/** isClean is true only if every check in {@code flags} passes. */
public record GroundResult(String itemId, boolean isClean, GroundFlags flags) {
}
