package com.vcnity.backend.security;

/**
 * One eligible, de-identified source supplied to the batch coding job.
 *
 * Consent and review eligibility must be established upstream.
 * This record validates data shape; it does not establish permission.
 */
public record CodingSource(
        String sourceRef,
        String text,
        Integer tier,
        String speakerCode
) {

    public CodingSource {
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceRef must not be blank"
            );
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Source text must not be blank"
            );
        }

        if (tier == null || (tier != 1 && tier != 2)) {
            throw new IllegalArgumentException(
                    "Coding requires tier 1 or tier 2"
            );
        }

        if (speakerCode != null && speakerCode.isBlank()) {
            throw new IllegalArgumentException(
                    "speakerCode must be null or non-blank"
            );
        }
    }
}