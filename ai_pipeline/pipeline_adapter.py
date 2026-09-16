"""Local synthetic-data adapter for the de-identification module."""

from uuid import uuid4

from ai_pipeline.deidentification import (
    DeidentificationError,
    deidentify_transcript,
)


# Only approved error codes may appear in exception records.
SAFE_ERROR_CODES = {
    "INVALID_TRANSCRIPT_ID",
    "INVALID_TRANSCRIPT_TEXT",
    "INVALID_ENTITY_DETECTOR",
    "INVALID_KEY_STORE",
    "ENTITY_DETECTION_FAILED",
    "INVALID_DETECTOR_OUTPUT",
    "INVALID_ENTITY",
    "EXISTING_PSEUDONYM_REQUIRES_REVIEW",
    "KEY_STORAGE_FAILED",
    "INVALID_KEY_REFERENCE",
}


def process_transcript(
    transcript_id,
    text,
    *,
    tier,
    consent_confirmed,
    synthetic,
    entity_detector,
    key_store,
):
    """Return either a review result or a safe exception record.

    Tier and consent must come from the trusted upstream workflow.
    These fields do not themselves implement authorisation.

    This adapter is limited to synthetic data for the local MVP.
    """

    # Generated internally; never derived from participant information.
    processing_id = uuid4().hex

    def stop(code):
        return {
            "processing_id": processing_id,
            "status": "blocked",
            "ready_for_ai": False,
            "result": None,
            "exception": {
                "processing_id": processing_id,
                "stage": "deidentification",
                "code": code,
                "review_required": True,
            },
        }

    # Stop ineligible input before the detector or key store is called.
    if synthetic is not True:
        return stop("SYNTHETIC_DATA_ONLY")

    if tier is None:
        return stop("UNTIERED_REFUSED")

    # bool is a subclass of int in Python, so use an exact type check.
    if type(tier) is not int or tier not in (1, 2, 3):
        return stop("INVALID_TIER")

    if tier == 3:
        return stop("TIER3_NOT_AUTO_PROCESSED")

    if consent_confirmed is not True:
        return stop("CONSENT_NOT_CONFIRMED")

    try:
        result = deidentify_transcript(
            transcript_id,
            text,
            entity_detector,
            key_store,
        )

    except DeidentificationError as error:
        code = (
            error.code
            if error.code in SAFE_ERROR_CODES
            else "DEIDENTIFICATION_FAILED"
        )
        return stop(code)

    except Exception:
        # Do not expose raw exception messages or transcript content.
        return stop("DEIDENTIFICATION_FAILED")

    return {
        "processing_id": processing_id,
        "status": "pending_human_review",
        "ready_for_ai": False,
        "result": result,
        "exception": None,
    }