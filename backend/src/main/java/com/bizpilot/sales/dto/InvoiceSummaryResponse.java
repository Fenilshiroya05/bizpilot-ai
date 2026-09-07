package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Used only by the paginated list/search endpoint — deliberately omits
 * {@code items}, mirroring {@code QuotationSummaryResponse} (see its Javadoc
 * for why a {@code JOIN FETCH} + {@code Pageable} combination is unsafe).
 * Full item detail is available via {@code GET /api/v1/invoices/{id}}.
 */
public record InvoiceSummaryResponse(
        UUID id,
        UUID customerId,
        InvoiceStatus status,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
