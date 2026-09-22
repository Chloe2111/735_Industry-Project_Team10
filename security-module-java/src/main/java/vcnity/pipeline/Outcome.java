package vcnity.pipeline;

/** What happened to one item as it moved through the pipeline. */
public record Outcome(
        String itemId,
        Stage stageReached,
        String reason,
        String theme,
        String quote,
        Double confidence,
        Integer redactionCount
) {

    public enum Stage { REJECTED_AT_GATE, EXCEPTIONS_QUEUE, CLEAN }

    public static Outcome rejected(String itemId, String reason) {
        return new Outcome(itemId, Stage.REJECTED_AT_GATE, reason, null, null, null, null);
    }

    public static Outcome exceptions(String itemId, String reason, String theme, String quote,
                                      double confidence, int redactionCount) {
        return new Outcome(itemId, Stage.EXCEPTIONS_QUEUE, reason, theme, quote, confidence, redactionCount);
    }

    public static Outcome clean(String itemId, String theme, String quote,
                                 double confidence, int redactionCount) {
        return new Outcome(itemId, Stage.CLEAN, null, theme, quote, confidence, redactionCount);
    }
}
