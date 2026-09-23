package com.vcnity.backend.security;

/**
 * A single AI-coded output item, as produced by the coding stage of the
 * pipeline, attributed to the speaker it came from.
 */
public record CodedItem(String itemId, String speakerCode) {
}
