package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DeidentifyTest {

    @Test
    void emailIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Contact me at jane.doe@example.com please.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("jane.doe@example.com"));
        assertTrue(result.redactedText().contains("[REDACTED:EMAIL]"));
    }

    @Test
    void phoneNumberIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Call me on 0412 345 678 tomorrow.", new HashSet<>(), new HashSet<>());
        assertTrue(result.redactedText().contains("[REDACTED:PHONE]"));
    }

    @Test
    void gazetteerTermIsRedacted() {
        Set<String> gazetteer = Set.of("Riverbend Community Hall");
        String text = "We ran the workshop at Riverbend Community Hall last week.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, gazetteer, new HashSet<>());
        assertFalse(result.redactedText().contains("Riverbend Community Hall"));
        assertTrue(result.redactedText().contains("[REDACTED:GAZETTEER]"));
    }

    @Test
    void gazetteerSupportsColloquialMultiWordPhrases() {
        Set<String> gazetteer = Set.of("the old crossing");
        String text = "We met at the old crossing after the session.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, gazetteer, new HashSet<>());
        assertFalse(result.redactedText().contains("the old crossing"));
        assertTrue(result.redactedText().contains("[REDACTED:GAZETTEER]"));
    }

    @Test
    void gazetteerTermNotPresentIsLeftAlone() {
        Set<String> gazetteer = Set.of("Riverbend Community Hall");
        String text = "The session ran smoothly with no issues.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, gazetteer, new HashSet<>());
        assertEquals(text, result.redactedText());
        assertEquals(0, result.entityCount());
    }

    @Test
    void lowercaseKinshipTermIsRedactedViaVernacularList() {
        Set<String> vernacular = Set.of("aunty");
        String text = "My aunty said the program helped a lot.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, new HashSet<>(), vernacular);
        assertFalse(result.redactedText().matches(".*\\baunty\\b.*"));
        assertTrue(result.redactedText().contains("[REDACTED:VERNACULAR]"));
    }

    @Test
    void vernacularMatchingIsCaseInsensitive() {
        Set<String> vernacular = Set.of("elder");
        String text = "She said the Elder had opened the session.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, new HashSet<>(), vernacular);
        assertTrue(result.redactedText().contains("[REDACTED:VERNACULAR]"));
    }

    @Test
    void knownEdgeCaseSentenceInitialVernacularTermIsRedactedButMislabeled() {
        Set<String> vernacular = Set.of("elder");
        String text = "The Elder who opened the session spoke first.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, new HashSet<>(), vernacular);
        assertFalse(result.redactedText().matches(".*\\bElder\\b.*"));
        assertEquals("capitalised_name", result.entities().get(0).category());
    }

    @Test
    void vernacularTermNotInListIsLeftAlone() {
        Set<String> vernacular = Set.of("aunty");
        String text = "The program covered general feedback about the garden.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, new HashSet<>(), vernacular);
        assertEquals(text, result.redactedText());
    }

    @Test
    void vernacularAndGazetteerDetectorsWorkTogether() {
        Set<String> gazetteer = Set.of("the old crossing");
        Set<String> vernacular = Set.of("aunty");
        String text = "My aunty mentioned that the old crossing used to flood every winter.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, gazetteer, vernacular);
        assertFalse(result.redactedText().matches(".*\\baunty\\b.*"));
        assertFalse(result.redactedText().contains("the old crossing"));
        int count = result.redactedText().split("\\[REDACTED", -1).length - 1;
        assertEquals(2, count);
    }

    @Test
    void capitalisedNameHeuristicCatchesLikelyNames() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Sarah Thompson led the discussion.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("Sarah Thompson"));
    }

    @Test
    void overlappingMatchesDoNotDuplicateOrCorruptText() {
        Set<String> gazetteer = Set.of("Jane Smith");
        String text = "Jane Smith can be reached at jane.smith@example.com.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, gazetteer, new HashSet<>());
        assertFalse(result.redactedText().contains("Jane Smith"));
        assertFalse(result.redactedText().contains("jane.smith@example.com"));
        int count = result.redactedText().split("\\[REDACTED", -1).length - 1;
        assertEquals(2, count);
    }
}