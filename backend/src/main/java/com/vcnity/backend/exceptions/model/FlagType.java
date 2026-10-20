package com.vcnity.backend.exceptions.model;

public enum FlagType {
    QUOTE_NOT_FOUND,
    LOW_CONFIDENCE,
    SOURCE_MISSING,
    CONTESTED,
    TIER_VIOLATION,
    REPRESENTATION_GAP,
    /** The AI failed or found no themes, so a person must read the de-identified text and code or reject it. */
    AI_COULD_NOT_CODE
}
