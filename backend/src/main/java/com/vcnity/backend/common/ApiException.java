package com.vcnity.backend.common;

import java.util.Map;

/**
 * Base class for errors that should reach the caller as a specific HTTP status with a
 * plain-language message. {@link ApiErrorHandler} turns every subclass into
 * { "message": "...", ...details } so all controllers answer errors the same way.
 */
public abstract class ApiException extends RuntimeException {

    private final int status;

    protected ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /** Extra fields to put in the error body next to "message". Empty by default. */
    public Map<String, Object> details() {
        return Map.of();
    }
}
