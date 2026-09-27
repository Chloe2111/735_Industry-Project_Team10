package com.vcnity.backend.security;

/**
 * A candidate finding returned by a coder.
 *
 * This is unvalidated output. The batch job must validate it
 * before creating a CodedFinding or running grounding checks.
 */
public record CodingDraft(
        String theme,
        String quote,
        Double confidence
) {
}