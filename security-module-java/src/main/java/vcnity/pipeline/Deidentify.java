package vcnity.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deidentify.java
 *
 * Runs on every Tier 2 item before it is sent to any AI model.
 *
 * Why this exists as a real module and not a TODO:
 * The adversarial review's single biggest finding was that
 * de-identification was ASSUMED in the pipeline design but never
 * actually built or tested. This is the fix.
 *
 * Why a gazetteer, not just a generic NER model:
 * Off-the-shelf entity recognisers are trained mostly on Anglo/Western
 * names and text. They reliably miss:
 *   - Community and Indigenous personal names
 *   - Kinship terms that function as identifying references
 *   - Local place names that aren't in a generic gazetteer
 *
 * So this module runs standard pattern-based detectors (email, phone,
 * etc.) AND a project-specific gazetteer that the community itself must
 * review and populate - see communityGazetteer.txt. This module ships
 * with only a placeholder gazetteer file: DO NOT deploy with the
 * placeholder entries. The real list must be built with, and signed off
 * by, the community whose material this is.
 */
public final class Deidentify {

    public static final Pattern EMAIL_RE =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w-]+(?:\\.[\\w-]+)*");

    // Australian mobile (04XX XXX XXX) or landline ((0X) XXXX XXXX) - loose
    // pattern, tune for your real data / add international formats as needed
    public static final Pattern PHONE_RE = Pattern.compile(
            "(?:\\+?61[ -]?)?(?:\\(0\\)|0)?4\\d{2}[ -]?\\d{3}[ -]?\\d{3}\\b"
                    + "|"
                    + "(?:\\+?61[ -]?)?\\(?0[2378]\\)?[ -]?\\d{4}[ -]?\\d{4}\\b"
    );

    // Simple capitalised-multi-word heuristic for names not caught elsewhere
    // (deliberately conservative - false positives are safer than false
    // negatives here, since a human reviews every redaction)
    public static final Pattern CAPITALISED_NAME_RE =
            Pattern.compile("\\b[A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,2}\\b");

    private Deidentify() {
    }

    public record Entity(String text, int start, int end, String category) {
    }

    public record Result(String originalText, String redactedText, List<Entity> entities) {
        public int entityCount() {
            return entities.size();
        }
    }

    /**
     * Loads one term per line. Blank lines and lines starting with '#'
     * are ignored. Matching is case-insensitive and on whole-word
     * boundaries only.
     */
    public static Set<String> loadGazetteer(String path) {
        Path p = Path.of(path);
        if (!Files.exists(p)) {
            return new LinkedHashSet<>();
        }
        Set<String> terms = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                terms.add(trimmed);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return terms;
    }

    private static List<Entity> findGazetteerHits(String text, Set<String> gazetteer) {
        List<Entity> hits = new ArrayList<>();
        for (String term : gazetteer) {
            Pattern pattern = Pattern.compile(
                    "(?<!\\w)" + Pattern.quote(term) + "(?!\\w)", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                hits.add(new Entity(m.group(), m.start(), m.end(), "gazetteer"));
            }
        }
        return hits;
    }

    private static List<Entity> findPatternHits(String text, Pattern pattern, String category) {
        List<Entity> hits = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            hits.add(new Entity(m.group(), m.start(), m.end(), category));
        }
        return hits;
    }

    public static Result deidentify(String text, Set<String> gazetteer) {
        return deidentify(text, gazetteer, true);
    }

    /**
     * Returns the original text, the redacted text, and the list of
     * entities found. Keep the entities list - it's your audit trail
     * for the privacy checklist sign-off, and it's how you catch
     * gazetteer gaps over time.
     */
    public static Result deidentify(String text, Set<String> gazetteer, boolean redactCapitalisedNames) {
        List<Entity> entities = new ArrayList<>();
        entities.addAll(findPatternHits(text, EMAIL_RE, "email"));
        entities.addAll(findPatternHits(text, PHONE_RE, "phone"));
        entities.addAll(findGazetteerHits(text, gazetteer));
        if (redactCapitalisedNames) {
            entities.addAll(findPatternHits(text, CAPITALISED_NAME_RE, "capitalised_name"));
        }

        // Resolve overlaps: keep the longest match at each position.
        // (List.sort is stable, so ties keep the order entities were
        // added above - same behaviour as the JS version's Array.sort.)
        entities.sort((a, b) -> {
            int byStart = Integer.compare(a.start(), b.start());
            if (byStart != 0) {
                return byStart;
            }
            int aLen = a.end() - a.start();
            int bLen = b.end() - b.start();
            return Integer.compare(bLen, aLen);
        });

        List<Entity> kept = new ArrayList<>();
        int lastEnd = -1;
        for (Entity e : entities) {
            if (e.start() >= lastEnd) {
                kept.add(e);
                lastEnd = e.end();
            }
        }

        // Build redacted text right-to-left so earlier offsets stay valid.
        List<Entity> rightToLeft = new ArrayList<>(kept);
        rightToLeft.sort((a, b) -> Integer.compare(b.start(), a.start()));

        StringBuilder redacted = new StringBuilder(text);
        for (Entity e : rightToLeft) {
            String tag = "[REDACTED:" + e.category().toUpperCase() + "]";
            redacted.replace(e.start(), e.end(), tag);
        }

        return new Result(text, redacted.toString(), kept);
    }
}
