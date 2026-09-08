package com.bizpilot.ai.tools.dto;

import com.bizpilot.products.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A tool-facing view of a {@code Product} (Phase 17) — mirrors {@code
 * products.dto.ProductResponse} minus {@code organizationId}/{@code
 * categoryId} (a bare UUID with no conversational value on its own)/{@code
 * createdAt}/{@code updatedAt}.
 */
public record ProductToolResult(
        UUID id,
        String sku,
        String name,
        String description,
        String unit,
        BigDecimal price,
        BigDecimal taxPercentage,
        ProductStatus status
) {
}
