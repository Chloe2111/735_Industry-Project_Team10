package com.vcnity.backend.intake;

/** Thrown when the automated checks could not run. The submission is not stored and not published. */
public class PipelineUnavailableException extends RuntimeException {

    public PipelineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
