import unittest
from unittest.mock import Mock

from ai_pipeline.deidentification import (
    DeidentificationError,
    Entity,
    deidentify_transcript,
)


class DeidentificationTests(unittest.TestCase):

    def setUp(self):
        # Test doubles isolate our logic from spaCy and disk storage.
        self.detector = Mock()
        self.detector.detect.return_value = []

        self.store = Mock()
        self.store.save.return_value = "test_key_reference"

    def process(self, text, transcript_id="synthetic_001"):
        return deidentify_transcript(
            transcript_id,
            text,
            self.detector,
            self.store,
        )

    def test_repeated_identifier_and_line_breaks(self):
        self.detector.detect.return_value = [
            Entity(0, 5, "PERSON"),
            Entity(13, 18, "PERSON"),
        ]

        result = self.process("Alice spoke.\nAlice agreed.")

        self.assertEqual(
            result["deidentified_text"],
            "PERSON_001 spoke.\nPERSON_001 agreed.",
        )
        self.assertEqual(result["replacement_count"], 2)

        self.store.save.assert_called_once_with(
            "synthetic_001",
            [{
                "pseudonym": "PERSON_001",
                "original": "Alice",
                "category": "PERSON",
            }],
        )

    def test_different_people_receive_different_tokens(self):
        self.detector.detect.return_value = [
            Entity(0, 5, "PERSON"),
            Entity(10, 13, "PERSON"),
        ]

        result = self.process("Alice met Bob.")

        self.assertEqual(
            result["deidentified_text"],
            "PERSON_001 met PERSON_002.",
        )

    def test_mapping_is_excluded_from_normal_output(self):
        self.detector.detect.return_value = [
            Entity(0, 5, "PERSON"),
        ]

        result = self.process("Alice spoke.")

        self.assertNotIn("mapping", result)
        self.assertNotIn("Alice", str(result))
        self.assertEqual(result["key_ref"], "test_key_reference")
        self.assertTrue(result["review_required"])

    def test_no_entities_still_requires_review(self):
        text = "The workshop was useful."
        result = self.process(text)

        self.assertEqual(result["deidentified_text"], text)
        self.assertEqual(result["replacement_count"], 0)
        self.assertEqual(result["entity_types"], [])
        self.assertTrue(result["review_required"])
        self.store.save.assert_called_once_with("synthetic_001", [])

    def test_invalid_text_stops_before_detection_or_storage(self):
        for text in (None, "", "   ", 123, []):
            with self.subTest(text=text):
                with self.assertRaises(DeidentificationError) as caught:
                    self.process(text)

                self.assertEqual(
                    caught.exception.code,
                    "INVALID_TRANSCRIPT_TEXT",
                )

        self.detector.detect.assert_not_called()
        self.store.save.assert_not_called()

    def test_invalid_id_stops_before_detection_or_storage(self):
        for identifier in (None, "", "../file", "two words", 123):
            with self.subTest(identifier=identifier):
                with self.assertRaises(DeidentificationError) as caught:
                    self.process("Synthetic text.", identifier)

                self.assertEqual(
                    caught.exception.code,
                    "INVALID_TRANSCRIPT_ID",
                )

        self.detector.detect.assert_not_called()
        self.store.save.assert_not_called()

    def test_detector_failure_hides_original_error(self):
        self.detector.detect.side_effect = RuntimeError(
            "Sensitive original text"
        )

        with self.assertRaises(DeidentificationError) as caught:
            self.process("Synthetic text.")

        self.assertEqual(
            str(caught.exception),
            "ENTITY_DETECTION_FAILED",
        )
        self.store.save.assert_not_called()

    def test_storage_failure_returns_no_successful_result(self):
        self.store.save.side_effect = OSError(
            "Sensitive mapping contents"
        )

        with self.assertRaises(DeidentificationError) as caught:
            self.process("Synthetic text.")

        self.assertEqual(str(caught.exception), "KEY_STORAGE_FAILED")

    def test_invalid_entity_span_is_rejected(self):
        self.detector.detect.return_value = [
            Entity(0, 999, "PERSON"),
        ]

        with self.assertRaises(DeidentificationError) as caught:
            self.process("Alice.")

        self.assertEqual(caught.exception.code, "INVALID_ENTITY")
        self.store.save.assert_not_called()

    def test_email_takes_priority_over_overlapping_name(self):
        self.detector.detect.return_value = [
            Entity(0, 5, "PERSON"),
            Entity(0, 17, "EMAIL"),
        ]

        result = self.process("alice@example.com")

        self.assertEqual(result["deidentified_text"], "EMAIL_001")
        self.assertEqual(result["replacement_count"], 1)
        self.assertEqual(result["entity_types"], ["EMAIL"])

    def test_existing_pseudonym_requires_review(self):
        with self.assertRaises(DeidentificationError) as caught:
            self.process("PERSON_001 spoke.")

        self.assertEqual(
            caught.exception.code,
            "EXISTING_PSEUDONYM_REQUIRES_REVIEW",
        )
        self.store.save.assert_not_called()

    def test_invalid_storage_reference_is_rejected(self):
        self.store.save.return_value = ""

        with self.assertRaises(DeidentificationError) as caught:
            self.process("Synthetic text.")

        self.assertEqual(
            caught.exception.code,
            "INVALID_KEY_REFERENCE",
        )

    def test_transcripts_have_separate_mapping_calls(self):
        self.detector.detect.return_value = [
            Entity(0, 5, "PERSON"),
        ]

        self.process("Alice.", "synthetic_001")
        self.process("Alice.", "synthetic_002")

        identifiers = [
            call.args[0]
            for call in self.store.save.call_args_list
        ]

        self.assertEqual(
            identifiers,
            ["synthetic_001", "synthetic_002"],
        )


if __name__ == "__main__":
    unittest.main()