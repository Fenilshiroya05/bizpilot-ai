package com.bizpilot.tasks.exception;

/**
 * Business-rule-level invalid input that Bean Validation annotations can't
 * express cleanly (e.g. "status can't be set to CANCELLED via this
 * endpoint", "title can't be blanked out") — mirrors
 * {@code sales.exception.InvalidQuotationDataException}/
 * {@code InvalidLeadDataException}.
 */
public class InvalidTaskDataException extends RuntimeException {

    public InvalidTaskDataException(String message) {
        super(message);
    }
}
