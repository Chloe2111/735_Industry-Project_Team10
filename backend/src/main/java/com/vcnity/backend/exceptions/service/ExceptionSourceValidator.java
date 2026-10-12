package com.vcnity.backend.exceptions.service;

/**
 * Checks the source tag on an item that arrives through the public API (POST /api/exceptions).
 *
 * A feature that tags its queue items registers one of these, so nobody can post an item
 * that claims to belong to a record that does not exist.
 */
public interface ExceptionSourceValidator {

    /** The sourceType this validator answers for, e.g. "COMMUNITY_SUBMISSION". */
    String sourceType();

    /**
     * @throws IllegalArgumentException if sourceId does not name a real record that can take a review item
     */
    void validateSource(String sourceId);
}
