package com.vcnity.backend.commission;

/**
 * A single field failed a validation rule that Bean Validation can't
 * cleanly express (e.g. "must be one of these exact values"). Carries the
 * field name so CommissionController can report it the same way as a
 * MethodArgumentNotValidException field error.
 */
public class InvalidCommissionFieldException extends RuntimeException {

    private final String field;

    public InvalidCommissionFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
