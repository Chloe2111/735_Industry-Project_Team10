package vcnity.pipeline;

/**
 * One item entering the pipeline.
 *
 * itemId:      string identifier
 * tier:        1, 2, or 3 - null means "no tier assigned yet" (untiered).
 *              Tier must be assigned by a human / the community, never
 *              inferred automatically.
 * rawText:     the item's text content
 * mockProfile: only used when PipelineCore.MOCK_MODE is true, to force a
 *              specific (e.g. bad) coded output for demo purposes.
 */
public record Item(String itemId, Integer tier, String rawText, MockProfile mockProfile) {

    public Item(String itemId, Integer tier, String rawText) {
        this(itemId, tier, rawText, null);
    }
}
