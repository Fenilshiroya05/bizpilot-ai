package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown by {@code InvoiceService.update} when the target invoice's current
 * status is anything other than {@code DRAFT} (CLAUDE.md §15; project
 * instructions §6/§15/§20/§24 — "invoice must be DRAFT for updates," stated
 * without exception). A {@code 409 CONFLICT}, not a validation error — the
 * request shape may be perfectly valid, but the resource's current state
 * forbids the operation, same category as {@code CustomerArchivedException}/
 * {@code LeadArchivedException}.
 */
public class InvoiceNotEditableException extends RuntimeException {

    public InvoiceNotEditableException(UUID invoiceId) {
        super("Invoice " + invoiceId + " is not editable — only DRAFT invoices can be updated");
    }
}
