package com.bizpilot.sales.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "status can't be set to CANCELLED via this endpoint",
 * "items can't be replaced with an empty list") — mirrors
 * {@code InvalidQuotationDataException}. Distinct from
 * {@link InvoiceNotEditableException}, which is about the invoice's
 * *current state* forbidding the operation entirely, not the shape of the
 * request itself.
 */
public class InvalidInvoiceDataException extends RuntimeException {

    public InvalidInvoiceDataException(String message) {
        super(message);
    }
}
