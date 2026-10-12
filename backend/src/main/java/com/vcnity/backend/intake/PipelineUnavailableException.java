package com.vcnity.backend.intake;

import com.vcnity.backend.common.ApiException;

/** The automated checks could not run. The submission is not stored and not published. HTTP 503. */
public class PipelineUnavailableException extends ApiException {

    public PipelineUnavailableException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
