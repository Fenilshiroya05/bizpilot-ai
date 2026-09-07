package com.bizpilot.products.dto;

import com.bizpilot.products.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partial update (PATCH semantics): every field is optional and {@code null}
 * means "leave unchanged" — same convention as
 * {@code crm.dto.CustomerUpdateRequest}/{@code sales.dto.LeadUpdateRequest}.
 *
 * <p>{@code clearCategory} mirrors {@code LeadUpdateRequest.clearFollowUpDate}:
 * a plain nullable {@code categoryId} can't distinguish "leave unchanged"
 * from "remove the category," so this explicit flag provides that signal.
 * Defaults to {@code false} when omitted.
 */
public record ProductUpdateRequest(
        @Size(max = 50) String sku,

        @Size(max = 255) String name,

        @Size(max = 2000) String description,

        @Size(max = 20) String unit,

        @DecimalMin(value = "0", inclusive = true) BigDecimal price,

        @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "100", inclusive = true) BigDecimal taxPercentage,

        ProductStatus status,

        UUID categoryId,

        boolean clearCategory
) {
}
