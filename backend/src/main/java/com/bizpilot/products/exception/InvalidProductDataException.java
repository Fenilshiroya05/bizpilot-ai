package com.bizpilot.products.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "name/sku/unit can't be blanked out") — distinct
 * from field-shape validation, which is handled by
 * {@code MethodArgumentNotValidException}.
 */
public class InvalidProductDataException extends RuntimeException {

    public InvalidProductDataException(String message) {
        super(message);
    }
}
