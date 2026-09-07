package com.bizpilot.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code status} and {@code organizationId} are absent: status always
 * starts at {@link com.bizpilot.sales.entity.QuotationStatus#DRAFT}, and the
 * organization is always derived from the authenticated tenant context.
 * {@code subtotal}/{@code discountAmount}/{@code taxAmount}/{@code grandTotal}
 * are likewise absent — CLAUDE.md §14/§41 require these to always be
 * backend-computed, never accepted from the client.
 *
 * <p>{@code discountPercentage} defaults to {@code 0} if omitted — a
 * quotation with no discount is the common case.
 */
public record QuotationCreateRequest(
        @NotNull UUID customerId,

        LocalDate validUntil,

        @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "100", inclusive = true)
        BigDecimal discountPercentage,

        @NotEmpty @Valid List<QuotationItemRequest> items
) {
}
