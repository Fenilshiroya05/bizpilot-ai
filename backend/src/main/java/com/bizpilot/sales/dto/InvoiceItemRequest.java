package com.bizpilot.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Only {@code productId} and {@code quantity} are client-supplied.
 * {@code unitPrice}/{@code taxPercentage}/product name are never accepted
 * from the client — they are always snapshotted from the current
 * {@code Product} by {@code InvoiceService}, mirroring
 * {@code QuotationItemRequest}.
 *
 * <p>{@code @DecimalMax} (production-readiness audit finding, post-Phase-19):
 * mirrors {@code QuotationItemRequest}'s identical bound, closing the same
 * NUMERIC(19,4)-overflow gap for invoices.
 */
public record InvoiceItemRequest(
        @NotNull UUID productId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("999999.9999") BigDecimal quantity
) {
}
