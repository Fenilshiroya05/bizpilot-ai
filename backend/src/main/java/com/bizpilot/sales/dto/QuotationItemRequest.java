package com.bizpilot.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Only {@code productId} and {@code quantity} are client-supplied.
 * {@code unitPrice}/{@code taxPercentage}/product name are never accepted
 * from the client — they are always snapshotted from the current
 * {@code Product} by {@code QuotationService} (project instructions §5),
 * so a client can never set an arbitrary price for a line item.
 */
public record QuotationItemRequest(
        @NotNull UUID productId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity
) {
}
