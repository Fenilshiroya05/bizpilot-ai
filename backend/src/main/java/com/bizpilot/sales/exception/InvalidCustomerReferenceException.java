package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when a quotation or invoice create/update request references a
 * customer that does not exist, or exists but belongs to a different
 * organization — a customer from Organization B must never be attachable to
 * a business document in Organization A (project instructions §6/§17). Same
 * reasoning/status-code choice as {@code sales.exception.InvalidAssigneeException}
 * (Phase 8) and {@code products.exception.InvalidProductCategoryException}
 * (Phase 9): a referenced-entity validation failure on the request, not a
 * resource-not-found error, so it's a {@code 400}, not a {@code 404}. Shared
 * across {@code QuotationService} and {@code InvoiceService} (Phase 11) —
 * both reference {@code crm.entity.Customer} identically.
 */
public class InvalidCustomerReferenceException extends RuntimeException {

    public InvalidCustomerReferenceException(UUID customerId) {
        super("No such customer in this organization: " + customerId);
    }

    /**
     * Used when the customer exists and belongs to the current organization,
     * but is otherwise not a valid target for a new/updated reference
     * (currently: archived customers) — a Phase 10 security-review finding
     * noted that only tenant-scoped existence was being checked, not whether
     * the customer was still active.
     */
    public InvalidCustomerReferenceException(UUID customerId, String reason) {
        super("Customer " + customerId + " cannot be used: " + reason);
    }
}
