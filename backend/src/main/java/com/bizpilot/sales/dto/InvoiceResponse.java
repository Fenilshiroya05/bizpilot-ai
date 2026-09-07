package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID customerId,
        InvoiceStatus status,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        List<InvoiceItemResponse> items,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
