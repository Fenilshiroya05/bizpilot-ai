package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.InvoiceStatus;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — same convention as {@code QuotationUpdateRequest}.
 * {@code clearDueDate} mirrors {@code QuotationUpdateRequest.clearValidUntil}
 * — the explicit signal needed to clear an optional date field.
 *
 * <p><b>Immutability (CLAUDE.md §15, project instructions §6/§20)</b>: this
 * request is only accepted by {@code InvoiceService.update} when the target
 * invoice's current status is {@link InvoiceStatus#DRAFT} — for ANY other
 * current status (including {@code CANCELLED}), the entire request is
 * rejected with a conflict error before any field is examined, regardless of
 * which fields are set here. There is no partial-immutability carve-out for
 * {@code status} alone: once an invoice leaves {@code DRAFT}, this endpoint
 * cannot move it further (CLAUDE.md §15/§20/§24 each independently state
 * this without exception, and §21 frames any payment-status transition
 * mechanism as an optional, separately-built minimal API this phase does not
 * introduce) — see {@code InvoiceService}'s Javadoc.
 *
 * <p>{@code items}, when supplied, atomically <b>replaces</b> the entire line
 * item collection, mirroring {@code QuotationUpdateRequest.items}. Must be
 * non-empty if supplied (validated in {@code InvoiceService}).
 *
 * <p>{@code status} accepts every value except {@code CANCELLED} — reaching
 * {@code CANCELLED} is only possible through the dedicated
 * {@code DELETE /api/v1/invoices/{id}} endpoint, mirroring
 * {@code QuotationUpdateRequest}'s equivalent restriction.
 */
public record InvoiceUpdateRequest(
        UUID customerId,

        LocalDate dueDate,

        boolean clearDueDate,

        InvoiceStatus status,

        @Valid List<InvoiceItemRequest> items
) {
}
