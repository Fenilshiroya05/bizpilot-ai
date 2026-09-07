package com.bizpilot.crm.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "status can't be set to ARCHIVED via this endpoint",
 * "name can't be blanked out") — distinct from field-shape validation, which
 * is handled by {@code MethodArgumentNotValidException}.
 */
public class InvalidCustomerDataException extends RuntimeException {

    public InvalidCustomerDataException(String message) {
        super(message);
    }
}
