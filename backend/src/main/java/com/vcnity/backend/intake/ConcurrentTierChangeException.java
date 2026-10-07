package com.vcnity.backend.intake;

import com.vcnity.backend.common.ApiException;

/**
 * Someone else changed (or first classified) the group between the moment this person
 * looked at it and the moment they saved. Their change is refused instead of overwriting
 * the other one. HTTP 409.
 */
public class ConcurrentTierChangeException extends ApiException {

    public ConcurrentTierChangeException(String message) {
        super(409, message);
    }
}
