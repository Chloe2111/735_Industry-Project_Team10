package com.vcnity.backend.findings;

import com.vcnity.backend.security.CodedFinding;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for validated pipeline findings.
 *
 * Persistence does not grant human approval or publication permission.
 */
public interface FindingRepository {

    /**
     * Stores a finding without silently overwriting existing content.
     *
     * An identical record with the same itemId is an accepted retry.
     * Different content with an existing itemId is a conflict.
     * Storage failures must propagate to the caller.
     */
    CodedFinding save(CodedFinding finding);

    /**
     * Returns an existing finding, or empty when it does not exist.
     * A database failure must not be reported as a missing finding.
     */
    Optional<CodedFinding> findByItemId(String itemId);

    /**
     * Retrieves findings for one source, ordered by itemId.
     *
     * sourceRef must be nonblank.
     * page must be zero or greater.
     * size must be between 1 and 100.
     */
    FindingPage findBySourceRef(String sourceRef, int page, int size);

    /**
     * Bounded response for source-based retrieval.
     */
    record FindingPage(
            List<CodedFinding> findings,
            int page,
            int size,
            boolean hasNext
    ) {
        public FindingPage {
            if (findings == null) {
                throw new IllegalArgumentException(
                        "Findings must not be null"
                );
            }

            if (page < 0 || size < 1 || size > 100) {
                throw new IllegalArgumentException(
                        "Invalid pagination parameters"
                );
            }

            if (findings.size() > size) {
                throw new IllegalArgumentException(
                        "Findings exceed the requested page size"
                );
            }

            findings = List.copyOf(findings);
        }
    }
}