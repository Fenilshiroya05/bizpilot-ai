package com.bizpilot.products.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fields mirror CLAUDE.md §13 exactly. {@code status}/{@code organizationId}
 * are absent: status always starts at
 * {@link com.bizpilot.products.entity.ProductStatus#ACTIVE}, and the
 * organization is always derived from the authenticated tenant context.
 *
 * <p>{@code taxPercentage} is optional and defaults to {@code 0} (no tax) if
 * omitted — a reasonable default rather than forcing every product creation
 * to specify a rate, since tax-exempt products are a legitimate case.
 */
public record ProductCreateRequest(
        @NotBlank @Size(max = 50) String sku,

        @NotBlank @Size(max = 255) String name,

        @Size(max = 2000) String description,

        @NotBlank @Size(max = 20) String unit,

        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,

        @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "100", inclusive = true) BigDecimal taxPercentage,

        UUID categoryId
) {
}
