package vcnity.pipeline;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeidentifyTest {

    @Test
    public void emailIsRedacted() {
        Deidentify.Result result = Deidentify.deidentify("Contact me at jane.doe@example.com please.", Set.of());
        assertFalse(result.redactedText().contains("jane.doe@example.com"));
        assertTrue(result.redactedText().contains("[REDACTED:EMAIL]"));
    }

    @Test
    public void phoneNumberIsRedacted() {
        Deidentify.Result result = Deidentify.deidentify("Call me on 0412 345 678 tomorrow.", Set.of());
        assertTrue(result.redactedText().contains("[REDACTED:PHONE]"));
    }

    @Test
    public void gazetteerTermIsRedacted() {
        // This is the core point of the module: a term a generic NER
        // model would likely miss (a local hall name, here) still gets
        // caught because it's in the project-specific gazetteer.
        Set<String> gazetteer = Set.of("Riverbend Community Hall");
        String text = "We ran the workshop at Riverbend Community Hall last week.";
        Deidentify.Result result = Deidentify.deidentify(text, gazetteer);
        assertFalse(result.redactedText().contains("Riverbend Community Hall"));
        assertTrue(result.redactedText().contains("[REDACTED:GAZETTEER]"));
    }

    @Test
    public void gazetteerMatchIsCaseInsensitive() {
        Set<String> gazetteer = Set.of("Wattle Creek");
        String text = "Everyone met near wattle creek before the session.";
        Deidentify.Result result = Deidentify.deidentify(text, gazetteer);
        assertTrue(result.redactedText().contains("[REDACTED:GAZETTEER]"));
    }

    @Test
    public void gazetteerTermNotPresentIsLeftAlone() {
        Set<String> gazetteer = Set.of("Riverbend Community Hall");
        String text = "The session ran smoothly with no issues.";
        Deidentify.Result result = Deidentify.deidentify(text, gazetteer);
        assertEquals(text, result.redactedText());
        assertEquals(0, result.entityCount());
    }

    @Test
    public void capitalisedNameHeuristicCatchesLikelyNames() {
        Deidentify.Result result = Deidentify.deidentify("Sarah Thompson led the discussion.", Set.of());
        assertFalse(result.redactedText().contains("Sarah Thompson"));
    }

    @Test
    public void overlappingMatchesDoNotDuplicateOrCorruptText() {
        Set<String> gazetteer = Set.of("Jane Smith");
        String text = "Jane Smith can be reached at jane.smith@example.com.";
        Deidentify.Result result = Deidentify.deidentify(text, gazetteer);
        assertFalse(result.redactedText().contains("Jane Smith"));
        assertFalse(result.redactedText().contains("jane.smith@example.com"));
        assertEquals(2, countOccurrences(result.redactedText(), "[REDACTED"));
    }

    @Test
    public void loadGazetteerIgnoresCommentsAndBlankLines() throws IOException {
        Path tmpFile = Files.createTempFile("gazetteer-test-", ".txt");
        try {
            Files.writeString(tmpFile, "# a comment\n\nRiverbend Community Hall\n  \nWattle Creek\n");
            Set<String> terms = Deidentify.loadGazetteer(tmpFile.toString());
            assertEquals(new LinkedHashSet<>(List.of("Riverbend Community Hall", "Wattle Creek")), terms);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    public void loadGazetteerMissingFileReturnsEmptySet() {
        Set<String> terms = Deidentify.loadGazetteer("/nonexistent/path/gazetteer.txt");
        assertEquals(Set.of(), terms);
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
