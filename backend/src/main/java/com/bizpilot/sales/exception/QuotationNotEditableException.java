package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown by {@code QuotationService.update} when the target quotation's
 * current status is anything other than {@code DRAFT}. Mirrors {@link
 * InvoiceNotEditableException} exactly — a production-readiness audit
 * finding (post-Phase-19): {@code QuotationService.update} previously had no
 * status guard at all, so an ACCEPTED/REJECTED/EXPIRED/CANCELLED quotation
 * could still be silently mutated via a normal PATCH, unlike the
 * structurally identical, already-immutable {@code Invoice}. A {@code 409
 * CONFLICT} — the request shape may be perfectly valid, but the resource's
 * current state forbids the operation, same category as {@code
 * CustomerArchivedException}/{@code LeadArchivedException}/{@code
 * InvoiceNotEditableException}.
 */
public class QuotationNotEditableException extends RuntimeException {

    public QuotationNotEditableException(UUID quotationId) {
        super("Quotation " + quotationId + " is not editable — only DRAFT quotations can be updated");
    }
}
