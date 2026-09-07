package com.bizpilot.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuotationItemResponse(
        UUID id,
        UUID productId,
        String productNameSnapshot,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxPercentage,
        BigDecimal lineSubtotal,
        BigDecimal lineTaxAmount
) {
}
