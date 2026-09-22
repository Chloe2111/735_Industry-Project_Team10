import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from ai_pipeline.deidentification import LocalEntityDetector
from ai_pipeline.key_store import RestrictedFileKeyStore
from ai_pipeline.pipeline_adapter import process_transcript


class PipelineGuardTests(unittest.TestCase):
    """Check routing and failures without running the real model."""

    def setUp(self):
        self.detector = Mock()
        self.detector.detect.return_value = []

        self.store = Mock()
        self.store.save.return_value = "test_key_reference"

    def process(self, **changes):
        arguments = {
            "transcript_id": "synthetic_001",
            "text": "The workshop was useful.",
            "tier": 1,
            "consent_confirmed": True,
            "synthetic": True,
            "entity_detector": self.detector,
            "key_store": self.store,
        }
        arguments.update(changes)
        return process_transcript(**arguments)

    def assert_blocked_before_processing(self, expected_code, **changes):
        outcome = self.process(**changes)

        self.assertEqual(outcome["status"], "blocked")
        self.assertFalse(outcome["ready_for_ai"])
        self.assertIsNone(outcome["result"])
        self.assertEqual(outcome["exception"]["code"], expected_code)
        self.assertEqual(
            outcome["exception"]["processing_id"],
            outcome["processing_id"],
        )
        self.detector.detect.assert_not_called()
        self.store.save.assert_not_called()

    def test_untiered_input_is_blocked(self):
        self.assert_blocked_before_processing(
            "UNTIERED_REFUSED",
            tier=None,
        )

    def test_tier_three_is_blocked(self):
        self.assert_blocked_before_processing(
            "TIER3_NOT_AUTO_PROCESSED",
            tier=3,
        )

    def test_missing_consent_is_blocked(self):
        self.assert_blocked_before_processing(
            "CONSENT_NOT_CONFIRMED",
            consent_confirmed=False,
        )

    def test_non_synthetic_input_is_blocked(self):
        self.assert_blocked_before_processing(
            "SYNTHETIC_DATA_ONLY",
            synthetic=False,
        )

    def test_boolean_tier_is_invalid(self):
        self.assert_blocked_before_processing(
            "INVALID_TIER",
            tier=True,
        )

    def test_tier_two_success_waits_for_review(self):
        outcome = self.process(tier=2)

        self.assertEqual(outcome["status"], "pending_human_review")
        self.assertFalse(outcome["ready_for_ai"])
        self.assertTrue(outcome["result"]["review_required"])
        self.assertIsNone(outcome["exception"])
        self.detector.detect.assert_called_once()
        self.store.save.assert_called_once()

    def test_detection_failure_creates_safe_exception(self):
        self.detector.detect.side_effect = RuntimeError(
            "Alice Example is in the original transcript"
        )

        outcome = self.process(text="Alice Example spoke.")

        self.assertEqual(outcome["status"], "blocked")
        self.assertIsNone(outcome["result"])
        self.assertEqual(
            outcome["exception"]["code"],
            "ENTITY_DETECTION_FAILED",
        )
        self.assertNotIn("Alice Example", json.dumps(outcome))
        self.store.save.assert_not_called()

    def test_storage_failure_blocks_output(self):
        self.store.save.side_effect = OSError(
            "Original identifiers must not appear in errors"
        )

        outcome = self.process()

        self.assertEqual(outcome["status"], "blocked")
        self.assertFalse(outcome["ready_for_ai"])
        self.assertIsNone(outcome["result"])
        self.assertEqual(
            outcome["exception"]["code"],
            "KEY_STORAGE_FAILED",
        )
        self.assertNotIn("Original identifiers", json.dumps(outcome))


class LocalPipelineIntegrationTests(unittest.TestCase):
    """Exercise the installed model and real storage together."""

    @classmethod
    def setUpClass(cls):
        cls.detector = LocalEntityDetector()

    def test_synthetic_transcript_reaches_review_with_saved_mapping(self):
        text = (
            "My name is Alice Smith and I live in Brisbane.\n"
            "Please email alice@example.com or call 0491 570 006.\n"
            "Alice Smith helped organise the workshop."
        )

        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve() / "restricted_keys"
            store = RestrictedFileKeyStore(directory)

            outcome = process_transcript(
                transcript_id="synthetic_integration_001",
                text=text,
                tier=1,
                consent_confirmed=True,
                synthetic=True,
                entity_detector=self.detector,
                key_store=store,
            )

            self.assertEqual(
                outcome["status"],
                "pending_human_review",
            )
            self.assertFalse(outcome["ready_for_ai"])
            self.assertIsNone(outcome["exception"])

            result = outcome["result"]

            self.assertEqual(
                result["deidentified_text"],
                "My name is PERSON_001 and I live in PLACE_001.\n"
                "Please email EMAIL_001 or call PHONE_001.\n"
                "PERSON_001 helped organise the workshop.",
            )
            self.assertEqual(result["replacement_count"], 5)
            self.assertTrue(result["review_required"])

            mapping_file = directory / f"{result['key_ref']}.json"
            self.assertTrue(mapping_file.is_file())

            saved = json.loads(
                mapping_file.read_text(encoding="utf-8")
            )

            self.assertEqual(
                saved["transcript_id"],
                "synthetic_integration_001",
            )
            self.assertEqual(len(saved["mapping"]), 4)

            originals = {
                item["original"] for item in saved["mapping"]
            }
            self.assertEqual(
                originals,
                {
                    "Alice Smith",
                    "Brisbane",
                    "alice@example.com",
                    "0491 570 006",
                },
            )

            # Original values belong in restricted storage only.
            ordinary_output = json.dumps(outcome)
            for original in originals:
                self.assertNotIn(original, ordinary_output)


if __name__ == "__main__":
    unittest.main()