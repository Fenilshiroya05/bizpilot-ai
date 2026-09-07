package com.bizpilot.sales.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "status can't be set to CANCELLED via this endpoint",
 * "items can't be replaced with an empty list") — distinct from field-shape
 * validation, which is handled by {@code MethodArgumentNotValidException}.
 */
public class InvalidQuotationDataException extends RuntimeException {

    public InvalidQuotationDataException(String message) {
        super(message);
    }
}
