package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when a quotation create/update request references a customer that
 * does not exist, or exists but belongs to a different organization — a
 * customer from Organization B must never be attachable to a quotation in
 * Organization A (project instructions §6/§17). Same reasoning/status-code
 * choice as {@code sales.exception.InvalidAssigneeException} (Phase 8) and
 * {@code products.exception.InvalidProductCategoryException} (Phase 9): a
 * referenced-entity validation failure on the request, not a resource-not-found
 * error, so it's a {@code 400}, not a {@code 404}.
 */
public class InvalidCustomerReferenceException extends RuntimeException {

    public InvalidCustomerReferenceException(UUID customerId) {
        super("No such customer in this organization: " + customerId);
    }

    /**
     * Used when the customer exists and belongs to the current organization,
     * but is otherwise not a valid target for a new/updated quotation
     * reference (currently: archived customers) — a quotation review finding
     * (Phase 10) noted that only tenant-scoped existence was being checked,
     * not whether the customer was still active.
     */
    public InvalidCustomerReferenceException(UUID customerId, String reason) {
        super("Customer " + customerId + " cannot be used on a quotation: " + reason);
    }
}
