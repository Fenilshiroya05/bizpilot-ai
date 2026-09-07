package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.InvoiceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deterministic, backend-only invoice total calculation (CLAUDE.md §15) — a
 * pure, stateless class with no Spring/persistence dependency, mirroring
 * {@code QuotationCalculator}'s architecture exactly, so it stays trivially
 * unit-testable in isolation from the service/repository layer.
 *
 * <p><b>No discount</b>: unlike {@code QuotationCalculator}, there is no
 * discount step — CLAUDE.md §15 never mentions an invoice discount (a
 * confirmed, deliberate omission). {@code total = subtotal + taxAmount}.
 *
 * <p><b>Monetary precision</b>: {@link #MONEY_SCALE} (4 decimal places) and
 * {@link #ROUNDING_MODE} ({@link RoundingMode#HALF_UP}) — the same
 * {@code NUMERIC(19,4)} convention used by {@code Product}/{@code Quotation}
 * since Phase 9/10.
 */
public final class InvoiceCalculator {

    public static final int MONEY_SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private InvoiceCalculator() {
        // static utility class
    }

    /**
     * Computes and applies every line item's {@code lineSubtotal}/
     * {@code lineTaxAmount} (via {@link InvoiceItem#applyCalculatedLineTotals})
     * as a side effect, then returns the invoice-level totals. Never reads a
     * line item's product's *current* price/tax — only the already
     * snapshotted {@code unitPrice}/{@code taxPercentage} stored on each
     * {@link InvoiceItem}.
     */
    public static Totals calculate(List<InvoiceItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);

        for (InvoiceItem item : items) {
            BigDecimal lineSubtotal = item.getQuantity().multiply(item.getUnitPrice())
                    .setScale(MONEY_SCALE, ROUNDING_MODE);
            BigDecimal lineTaxAmount = percentageOf(lineSubtotal, item.getTaxPercentage());

            item.applyCalculatedLineTotals(lineSubtotal, lineTaxAmount);

            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTaxAmount);
        }

        BigDecimal total = subtotal.add(taxAmount).setScale(MONEY_SCALE, ROUNDING_MODE);

        return new Totals(subtotal, taxAmount, total);
    }

    private static BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);
    }

    public record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
    }
}
