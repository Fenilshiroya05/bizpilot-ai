package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(
        UUID id,
        UUID customerId,
        QuotationStatus status,
        LocalDate validUntil,
        BigDecimal discountPercentage,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        List<QuotationItemResponse> items,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
