package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when a quotation item references a product that does not exist, or
 * exists but belongs to a different organization — same reasoning as
 * {@link InvalidCustomerReferenceException}.
 */
public class InvalidProductReferenceException extends RuntimeException {

    public InvalidProductReferenceException(UUID productId) {
        super("No such product in this organization: " + productId);
    }

    /**
     * Used when the product exists and belongs to the current organization,
     * but is otherwise not a valid target for a new quotation item
     * (currently: inactive products) — see
     * {@link InvalidCustomerReferenceException#InvalidCustomerReferenceException(java.util.UUID, String)}
     * for the equivalent customer-side rationale.
     */
    public InvalidProductReferenceException(UUID productId, String reason) {
        super("Product " + productId + " cannot be used on a quotation: " + reason);
    }
}
