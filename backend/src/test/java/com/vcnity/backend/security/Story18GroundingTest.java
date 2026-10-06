package com.vcnity.backend.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Story18GroundingTest {

    private CodedFinding finding(String sourceRef, String quote) {
        return new CodedFinding(
                "finding_001",
                sourceRef,
                "speaker_001",
                "Seating",
                quote,
                0.90,
                1,
                List.of()
        );
    }

    @Test
    void normalizesAllSpecifiedAsciiWhitespace() {
        String text = " \tWe\nneed\r\nmore\f\u000Bseating. \t";

        assertEquals(
                "we need more seating.",
                Grounding.normalize(text)
        );
    }

    @Test
    void lowercasingDoesNotDependOnDefaultLocale() {
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals(
                    "indoor seating",
                    Grounding.normalize("INDOOR SEATING")
            );
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void doesNotJoinSeparateWordsToCreateAQuote() {
        assertFalse(
                Grounding.quoteIsGrounded(
                        "nowhere",
                        "There is no where to sit."
                )
        );
    }

    @Test
    void treatsRegexCharactersAsLiteralText() {
        assertTrue(
                Grounding.quoteIsGrounded(
                        "A+B",
                        "Participants selected A+B."
                )
        );

        assertFalse(
                Grounding.quoteIsGrounded(
                        "A+B",
                        "Participants selected AAAB."
                )
        );
    }

    @Test
    void blankQuoteDoesNotPassGrounding() {
        assertFalse(
                Grounding.quoteIsGrounded(
                        " \t\n ",
                        "We need more seating."
                )
        );
    }

    @Test
    void structuredFindingUsesItsSourceReference() {
        CodedFinding input = finding(
                "source_001",
                "We need more seating."
        );

        Grounding.GroundingResult result =
                Grounding.groundFinding(
                        input,
                        Map.of(
                                "source_001",
                                "We need   more\nseating."
                        )
                );

        assertEquals("finding_001", result.itemId());
        assertTrue(result.isClean());
        assertFalse(result.flags().any());
    }

    @Test
    void quoteInAnotherSourceDoesNotPass() {
        CodedFinding input = finding(
                "source_001",
                "We need more seating."
        );

        Grounding.GroundingResult result =
                Grounding.groundFinding(
                        input,
                        Map.of(
                                "source_001", "We need better lighting.",
                                "source_002", "We need more seating."
                        )
                );

        assertFalse(result.isClean());
        assertTrue(result.flags().quoteNotGrounded);
        assertFalse(result.flags().sourceMissing);
    }

    @Test
    void missingReferencedSourceIsFlagged() {
        CodedFinding input = finding(
                "missing_source",
                "We need more seating."
        );

        Grounding.GroundingResult result =
                Grounding.groundFinding(
                        input,
                        Map.of(
                                "source_001",
                                "We need more seating."
                        )
                );

        assertFalse(result.isClean());
        assertTrue(result.flags().sourceMissing);
    }

    @Test
    void structuredFindingRejectsNonFiniteConfidence() {
        for (double confidence : new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CodedFinding(
                            "finding_001",
                            "source_001",
                            "speaker_001",
                            "Seating",
                            "We need more seating.",
                            confidence,
                            1,
                            List.of()
                    )
            );
        }
    }
}