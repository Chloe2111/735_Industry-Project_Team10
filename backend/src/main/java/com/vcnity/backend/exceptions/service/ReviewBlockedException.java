package com.vcnity.backend.exceptions.service;

import com.vcnity.backend.common.ApiException;

/**
 * Thrown when a reviewer tries to clear an item that must not be cleared right now.
 * The item keeps its old status, and the reviewer is told why (HTTP 409) instead of seeing a false success.
 */
public class ReviewBlockedException extends ApiException {

    public ReviewBlockedException(String message) {
        super(409, message);
    }
}
