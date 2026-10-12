package com.vcnity.backend.intake;

import com.vcnity.backend.common.ApiException;

/**
 * A reviewer asked to publish a submission that cannot be published right now: a flag is still
 * pending or was rejected, it is held, or its group is Tier 3. Nothing is changed. HTTP 409.
 */
public class PublishRefusedException extends ApiException {

    public PublishRefusedException(String message) {
        super(409, message);
    }
}
