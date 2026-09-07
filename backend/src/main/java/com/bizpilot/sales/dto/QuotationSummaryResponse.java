package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Used only by the paginated list/search endpoint — deliberately omits
 * {@code items}. A {@code JOIN FETCH} on {@code Quotation.items} cannot be
 * combined with {@code Pageable} without breaking pagination (see
 * {@code QuotationRepository.search}'s Javadoc), so the list query never
 * initializes the lazy items collection at all; a response shape that
 * doesn't need it avoids the problem entirely rather than working around it.
 * Full item detail is available via {@code GET /api/v1/quotations/{id}}.
 */
public record QuotationSummaryResponse(
        UUID id,
        UUID customerId,
        QuotationStatus status,
        LocalDate validUntil,
        BigDecimal discountPercentage,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
