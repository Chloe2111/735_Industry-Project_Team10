"""Local demonstration using entirely synthetic transcript content."""

import json
import stat

from ai_pipeline.deidentification import (
    DeidentificationError,
    LocalEntityDetector,
    deidentify_transcript,
)
from ai_pipeline.key_store import RestrictedFileKeyStore


def main():
    # Synthetic example only. This does not implement the intake gate.
    transcript = (
        "My name is Alice Smith and I live in Brisbane.\n"
        "Please email alice@example.com or call 0491 570 006.\n"
        "Alice Smith helped organise the workshop."
    )

    try:
        detector = LocalEntityDetector()
        store = RestrictedFileKeyStore()

        result = deidentify_transcript(
            transcript_id="synthetic_001",
            text=transcript,
            entity_detector=detector,
            key_store=store,
        )

    except DeidentificationError as error:
        print(f"Demo stopped: {error.code}")
        return

    except Exception:
        print("Demo stopped: STORAGE_OR_SETUP_FAILED")
        return

    print("DE-IDENTIFIED RESULT")
    print(json.dumps(result, indent=2))

    # Verify the stored file without printing its original identifiers.
    mapping_file = store.directory / f"{result['key_ref']}.json"

    try:
        permissions = stat.S_IMODE(mapping_file.stat().st_mode)
        stored = json.loads(mapping_file.read_text(encoding="utf-8"))

        mapping_matches = (
            stored["transcript_id"] == result["transcript_id"]
            and isinstance(stored["mapping"], list)
        )

        print("\nSTORAGE CHECK")
        print(f"Mapping file exists: {mapping_file.is_file()}")
        print(f"File permissions: {oct(permissions)}")
        print(f"Mapping structure and transcript ID valid: {mapping_matches}")

    except Exception:
        print("Storage verification failed.")
        return

    print("\nReview the output for missed or incorrect replacements.")
    print("This demo does not establish full de-identification accuracy.")


if __name__ == "__main__":
    main()