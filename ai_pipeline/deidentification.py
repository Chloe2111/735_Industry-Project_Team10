"""De-identification MVP for synthetic English transcripts.

Call only after the upstream tier and consent checks.

Detection is imperfect. Every successful result requires human review.
"""

import re
from dataclasses import dataclass

import spacy


class DeidentificationError(Exception):
    """A controlled error whose message contains no transcript data."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


@dataclass(frozen=True)
class Entity:
    """Character positions in the original text; end is exclusive."""

    start: int
    end: int
    category: str


class LocalEntityDetector:
    """Use local spaCy detection plus email and Australian phone rules."""

    EMAIL_PATTERN = re.compile(
        r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"
    )

    # Initial scope: Australian mobile and geographic phone formats.
    PHONE_PATTERN = re.compile(
        r"(?<!\w)"
        r"(?:\+61[\s.-]?[23478]|0[23478]|\(0[2378]\))"
        r"(?:[\s.-]?\d){8}"
        r"(?!\w)"
    )

    LABELS = {
        "PERSON": "PERSON",
        "GPE": "PLACE",
        "LOC": "PLACE",
        "FAC": "PLACE",
    }

    def __init__(self, model_name="en_core_web_sm"):
        try:
            self.nlp = spacy.load(model_name)
        except Exception:
            raise DeidentificationError("MODEL_LOAD_FAILED") from None

    def detect(self, text):
        candidates = []

        # Structured identifiers take precedence over overlapping NLP spans.
        for pattern, category in (
            (self.EMAIL_PATTERN, "EMAIL"),
            (self.PHONE_PATTERN, "PHONE"),
        ):
            for match in pattern.finditer(text):
                candidates.append(
                    Entity(match.start(), match.end(), category)
                )

        document = self.nlp(text)

        for entity in document.ents:
            category = self.LABELS.get(entity.label_)
            if category:
                candidates.append(
                    Entity(entity.start_char, entity.end_char, category)
                )

        return candidates


def _validate_and_resolve_entities(entities, text_length):
    """Validate spans, then prioritise structured and longer matches."""

    if not isinstance(entities, (list, tuple)):
        raise DeidentificationError("INVALID_DETECTOR_OUTPUT")

    allowed_categories = {"PERSON", "PLACE", "EMAIL", "PHONE"}

    for entity in entities:
        if not isinstance(entity, Entity):
            raise DeidentificationError("INVALID_ENTITY")

        if (
            type(entity.start) is not int
            or type(entity.end) is not int
            or not isinstance(entity.category, str)
            or entity.category not in allowed_categories
            or not 0 <= entity.start < entity.end <= text_length
        ):
            raise DeidentificationError("INVALID_ENTITY")

    priorities = {"EMAIL": 0, "PHONE": 1, "PERSON": 2, "PLACE": 2}

    ordered = sorted(
        entities,
        key=lambda entity: (
            priorities[entity.category],
            -(entity.end - entity.start),
            entity.start,
            entity.category,
        ),
    )

    selected = []

    for candidate in ordered:
        overlaps = any(
            candidate.start < existing.end
            and existing.start < candidate.end
            for existing in selected
        )

        if not overlaps:
            selected.append(candidate)

    return sorted(selected, key=lambda entity: entity.start)


def deidentify_transcript(
    transcript_id,
    text,
    entity_detector,
    key_store,
):
    """Replace detected identifiers and persist a separate mapping.

    entity_detector must provide detect(text).
    key_store must provide save(transcript_id, mapping), returning an
    opaque non-empty string reference after a successful restricted write.

    The mapping is passed only to key_store, never included in the result.
    """

    # IDs should be generated source IDs, never participant names.
    if (
        not isinstance(transcript_id, str)
        or re.fullmatch(r"[A-Za-z0-9_-]{1,80}", transcript_id) is None
    ):
        raise DeidentificationError("INVALID_TRANSCRIPT_ID")

    if not isinstance(text, str) or not text.strip():
        raise DeidentificationError("INVALID_TRANSCRIPT_TEXT")

    if not callable(getattr(entity_detector, "detect", None)):
        raise DeidentificationError("INVALID_ENTITY_DETECTOR")

    if not callable(getattr(key_store, "save", None)):
        raise DeidentificationError("INVALID_KEY_STORE")

    try:
        detected = entity_detector.detect(text)
    except Exception:
        raise DeidentificationError("ENTITY_DETECTION_FAILED") from None

    entities = _validate_and_resolve_entities(detected, len(text))

    # Avoid confusing existing tokens with newly generated pseudonyms.
    if re.search(r"\b(?:PERSON|PLACE|EMAIL|PHONE)_\d+\b", text):
        raise DeidentificationError("EXISTING_PSEUDONYM_REQUIRES_REVIEW")

    replacements = {}
    counters = {}
    mapping = []
    output_parts = []
    cursor = 0

    for entity in entities:
        original = text[entity.start:entity.end]

        # Exact same text and category share a token within this transcript.
        identity = (entity.category, original)

        if identity not in replacements:
            counters[entity.category] = counters.get(entity.category, 0) + 1
            pseudonym = (
                f"{entity.category}_{counters[entity.category]:03d}"
            )
            replacements[identity] = pseudonym

            mapping.append(
                {
                    "pseudonym": pseudonym,
                    "original": original,
                    "category": entity.category,
                }
            )

        output_parts.append(text[cursor:entity.start])
        output_parts.append(replacements[identity])
        cursor = entity.end

    output_parts.append(text[cursor:])
    deidentified_text = "".join(output_parts)

    try:
        key_ref = key_store.save(transcript_id, mapping)
    except Exception:
        raise DeidentificationError("KEY_STORAGE_FAILED") from None

    if not isinstance(key_ref, str) or not key_ref.strip():
        raise DeidentificationError("INVALID_KEY_REFERENCE")

    return {
        "transcript_id": transcript_id,
        "deidentified_text": deidentified_text,
        "replacement_count": len(entities),
        "entity_types": sorted({entity.category for entity in entities}),
        "key_ref": key_ref,
        "review_required": True,
    }