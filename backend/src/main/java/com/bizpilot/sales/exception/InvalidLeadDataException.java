package com.bizpilot.sales.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "name can't be blanked out") — distinct from
 * field-shape validation, which is handled by
 * {@code MethodArgumentNotValidException}.
 */
public class InvalidLeadDataException extends RuntimeException {

    public InvalidLeadDataException(String message) {
        super(message);
    }
}
