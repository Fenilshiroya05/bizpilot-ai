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
 * {@code Product} by {@code QuotationService} (project instructions §5),
 * so a client can never set an arbitrary price for a line item.
 *
 * <p>{@code @DecimalMax} (production-readiness audit finding, post-Phase-19):
 * an unbounded quantity combined with an unbounded {@code Product.price}
 * (also newly bounded, see {@code ProductCreateRequest}) could otherwise
 * produce a line total exceeding {@code NUMERIC(19,4)}'s capacity, failing
 * as a raw, unmapped database "numeric field overflow" error (a confusing
 * {@code 500} rather than a clean {@code 400}) instead of being rejected at
 * the validation boundary. 999999.9999 comfortably exceeds any realistic
 * business quantity.
 */
public record QuotationItemRequest(
        @NotNull UUID productId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("999999.9999") BigDecimal quantity
) {
}
