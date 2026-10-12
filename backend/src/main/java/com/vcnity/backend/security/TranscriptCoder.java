package com.vcnity.backend.security;

import java.util.List;

/**
 * Contract for coding eligible, de-identified transcript text.
 * Upstream processing must establish permission before calling it.
 */
@FunctionalInterface
public interface TranscriptCoder {

    /**
     * Returns unvalidated candidate findings.
     * An empty list means no findings.
     * Null results or null entries are invalid.
     * Exceptions indicate coding failure.
     */
    List<CodingDraft> code(String deidentifiedText) throws Exception;
}
