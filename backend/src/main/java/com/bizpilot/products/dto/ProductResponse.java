package com.bizpilot.products.dto;

import com.bizpilot.products.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        String unit,
        BigDecimal price,
        BigDecimal taxPercentage,
        ProductStatus status,
        UUID categoryId,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
