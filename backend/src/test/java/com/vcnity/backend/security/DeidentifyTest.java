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

    // --- v3: single first names, ages, dates (closes the gap found in the
    //     real end-to-end test where "Tina, 34, ... 3 March" was stored
    //     and sent to the AI completely unredacted) ---

    @Test
    void singleFirstNameIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("I spoke to Maria about it yesterday.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("Maria"));
        assertTrue(result.redactedText().contains("[REDACTED:SINGLE_NAME]"));
    }

    @Test
    void ageInCommaFormIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Tina, 34, said the park needs better lighting.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().matches(".*,\\s*34\\s*,.*"));
        assertTrue(result.redactedText().contains("[REDACTED:AGE]"));
    }

    @Test
    void agedPhraseIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("My neighbour Raj, aged 67, stopped walking that route.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("aged 67"));
    }

    @Test
    void yearsOldPhraseIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("She is 29 years old and lives nearby.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("29 years old"));
        assertTrue(result.redactedText().contains("[REDACTED:AGE]"));
    }

    @Test
    void dayMonthDateIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("She said on 3 March that the lighting was poor.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("3 March"));
        assertTrue(result.redactedText().contains("[REDACTED:DATE]"));
    }

    @Test
    void monthDayDateIsRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("We met again on August 15 to follow up.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("August 15"));
    }

    @Test
    void monthNameAloneIsNotWronglyRedactedAsASingleName() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("I'll see you in March for the next session.", new HashSet<>(), new HashSet<>());
        assertTrue(result.redactedText().contains("March"));
    }

    @Test
    void commonSentenceStartersAreNotWronglyRedactedAsSingleNames() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("It feels unsafe walking home after dark.", new HashSet<>(), new HashSet<>());
        assertTrue(result.redactedText().startsWith("It feels unsafe"));
    }

    // --- v4: sentence/field-start words are not redacted unless followed
    //     by a comma, closing the 3 FacilitatorCodingTest failures the
    //     team found live, where "Idea", "Problem", "Who" and "Local"
    //     (community-idea form field labels) were wrongly redacted ---

    @Test
    void formFieldLabelsAreNotRedacted() {
        String text = "Idea title: the park needs lighting. Problem it solves: it is unsafe. "
                + "Who would benefit: Local families.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(text, new HashSet<>(), new HashSet<>());
        String out = result.redactedText();
        assertTrue(out.contains("Idea title:"));
        assertTrue(out.contains("Problem it solves:"));
        assertTrue(out.contains("Who would benefit:"));
        assertTrue(out.contains("Local families"));
    }

    @Test
    void sentenceStartNameWithoutCommaIsLeftAlone() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Maria led the session well.", new HashSet<>(), new HashSet<>());
        assertTrue(result.redactedText().startsWith("Maria"));
    }

    @Test
    void sentenceStartNameWithCommaIsStillRedacted() {
        Deidentify.DeidentificationResult result =
                Deidentify.deidentify("Maria, 29, led the session well.", new HashSet<>(), new HashSet<>());
        assertFalse(result.redactedText().contains("Maria"));
    }

    @Test
    void realSubmissionFromEndToEndTestIsFullyRedacted() {
        // This is the exact text from the live MongoDB record that exposed
        // the gap: single names, ages and dates were all stored unredacted.
        String real = "Idea title: Tina, 34, said on 3 March that the park needs "
                + "better lighting near the bus stop. My neighbour Raj, aged 67, "
                + "stopped walking that route.";
        Deidentify.DeidentificationResult result = Deidentify.deidentify(real, new HashSet<>(), new HashSet<>());
        String out = result.redactedText();
        assertFalse(out.contains("Tina"));
        assertFalse(out.contains("Raj"));
        assertFalse(out.contains("3 March"));
        assertFalse(out.matches(".*,\\s*34\\s*,.*"));
        assertFalse(out.contains("aged 67"));
    }
}