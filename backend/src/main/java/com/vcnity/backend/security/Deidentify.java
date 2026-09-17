package com.vcnity.backend.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
 * v2 — adds a fourth detector: vernacular/kinship terms (aunty, elder,
 * mob...), closing DEF-1 from the blind-spot test. These are common
 * nouns, not proper nouns, so they get their own governed list
 * (vernacularTerms.txt) rather than being mixed into the gazetteer.
 * The underlying matching logic is identical to the gazetteer's — the
 * separation is a governance/audit-trail choice, not a new algorithm.
 *
 * Both term-list files ship with ONLY placeholder entries. DO NOT
 * deploy with the placeholder entries. The real lists must be built
 * with, and signed off by, the community whose material this is.
 */
public final class Deidentify {

    private static final Pattern EMAIL_RE = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w-]+(?:\\.[\\w-]+)*");

    private static final Pattern PHONE_RE = Pattern.compile(
            "(?:\\+?61[ -]?)?(?:\\(0\\)|0)?4\\d{2}[ -]?\\d{3}[ -]?\\d{3}\\b"
                    + "|"
                    + "(?:\\+?61[ -]?)?\\(?0[2378]\\)?[ -]?\\d{4}[ -]?\\d{4}\\b");

    private static final Pattern CAPITALISED_NAME_RE = Pattern.compile("\\b[A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,2}\\b");

    private Deidentify() {
    }

    public record Entity(String text, int start, int end, String category) {
    }

    public record DeidentificationResult(String originalText, String redactedText, List<Entity> entities) {
        public int entityCount() {
            return entities.size();
        }
    }

    public static Set<String> loadTermList(Path path) {
        Set<String> terms = new LinkedHashSet<>();
        if (path == null || !Files.exists(path)) {
            return terms;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                terms.add(trimmed);
            }
        } catch (IOException e) {
            // Match the JS behaviour: a missing/unreadable file yields an
            // empty term set rather than crashing the pipeline.
        }
        return terms;
    }

    public static Set<String> loadTermListFromClasspath(String resourceName) {
        Set<String> terms = new LinkedHashSet<>();
        try (InputStream is = Deidentify.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                return terms;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    terms.add(trimmed);
                }
            }
        } catch (IOException e) {
            // same fallback as above
        }
        return terms;
    }

    private static List<Entity> findTermListHits(String text, Set<String> terms, String category) {
        List<Entity> hits = new ArrayList<>();
        for (String term : terms) {
            Pattern pattern = Pattern.compile("(?<!\\w)" + Pattern.quote(term) + "(?!\\w)", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                hits.add(new Entity(m.group(), m.start(), m.end(), category));
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

    public static DeidentificationResult deidentify(String text, Set<String> gazetteer, Set<String> vernacularTerms) {
        return deidentify(text, gazetteer, vernacularTerms, true);
    }

    public static DeidentificationResult deidentify(
            String text, Set<String> gazetteer, Set<String> vernacularTerms, boolean redactCapitalisedNames) {

        List<Entity> entities = new ArrayList<>();
        entities.addAll(findPatternHits(text, EMAIL_RE, "email"));
        entities.addAll(findPatternHits(text, PHONE_RE, "phone"));
        entities.addAll(findTermListHits(text, gazetteer, "gazetteer"));
        entities.addAll(findTermListHits(text, vernacularTerms, "vernacular"));
        if (redactCapitalisedNames) {
            entities.addAll(findPatternHits(text, CAPITALISED_NAME_RE, "capitalised_name"));
        }

        entities.sort(Comparator.<Entity>comparingInt(Entity::start)
                .thenComparingInt(e -> -(e.end() - e.start())));

        List<Entity> kept = new ArrayList<>();
        int lastEnd = -1;
        for (Entity e : entities) {
            if (e.start() >= lastEnd) {
                kept.add(e);
                lastEnd = e.end();
            }
        }

        StringBuilder redacted = new StringBuilder(text);
        List<Entity> rightToLeft = new ArrayList<>(kept);
        rightToLeft.sort(Comparator.comparingInt(Entity::start).reversed());
        for (Entity e : rightToLeft) {
            String tag = "[REDACTED:" + e.category().toUpperCase() + "]";
            redacted.replace(e.start(), e.end(), tag);
        }

        return new DeidentificationResult(text, redacted.toString(), kept);
    }
}
