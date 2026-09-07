package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when a quotation or invoice item references a product that does not
 * exist, or exists but belongs to a different organization — same reasoning
 * as {@link InvalidCustomerReferenceException}. Shared across
 * {@code QuotationService} and {@code InvoiceService} (Phase 11).
 */
public class InvalidProductReferenceException extends RuntimeException {

    public InvalidProductReferenceException(UUID productId) {
        super("No such product in this organization: " + productId);
    }

    /**
     * Used when the product exists and belongs to the current organization,
     * but is otherwise not a valid target for a new line item (currently:
     * inactive products) — see
     * {@link InvalidCustomerReferenceException#InvalidCustomerReferenceException(java.util.UUID, String)}
     * for the equivalent customer-side rationale.
     */
    public InvalidProductReferenceException(UUID productId, String reason) {
        super("Product " + productId + " cannot be used: " + reason);
    }
}
