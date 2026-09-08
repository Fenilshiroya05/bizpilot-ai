package com.bizpilot.ai.tools.dto;

import com.bizpilot.sales.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tool-facing view of an {@code Invoice} (Phase 17) — mirrors {@code
 * sales.dto.InvoiceSummaryResponse} minus {@code organizationId}/{@code
 * createdAt}/{@code updatedAt}; deliberately omits {@code items} (the
 * question this tool answers — "what's outstanding" — doesn't need
 * line-item detail, and including it for every result would needlessly
 * inflate the model's context).
 */
public record InvoiceToolResult(
        UUID id,
        UUID customerId,
        InvoiceStatus status,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total
) {
}
