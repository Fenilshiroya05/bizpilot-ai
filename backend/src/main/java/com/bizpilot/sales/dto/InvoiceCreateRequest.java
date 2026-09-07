package com.bizpilot.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code status} and {@code organizationId} are absent: status always starts
 * at {@link com.bizpilot.sales.entity.InvoiceStatus#DRAFT}, and the
 * organization is always derived from the authenticated tenant context.
 * {@code subtotal}/{@code taxAmount}/{@code total} are likewise absent —
 * CLAUDE.md §15 requires these to always be backend-computed, never accepted
 * from the client. No {@code discountPercentage} field — CLAUDE.md §15
 * never mentions an invoice discount (a confirmed, deliberate omission,
 * unlike {@code QuotationCreateRequest}). {@code dueDate} is optional, kept
 * deliberately minimal per project instructions.
 */
public record InvoiceCreateRequest(
        @NotNull UUID customerId,

        LocalDate dueDate,

        @NotEmpty @Valid List<InvoiceItemRequest> items
) {
}
