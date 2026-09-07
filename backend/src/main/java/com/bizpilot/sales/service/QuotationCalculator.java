package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.QuotationItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deterministic, backend-only quotation total calculation (CLAUDE.md §14,
 * §41) — a pure, stateless class with no Spring/persistence dependency so it
 * stays trivially unit-testable in isolation from the service/repository
 * layer, per project instructions §22 ("keep financial calculation logic
 * deterministic and easily testable").
 *
 * <p><b>Monetary precision</b>: {@link #MONEY_SCALE} (4 decimal places) and
 * {@link #ROUNDING_MODE} ({@link RoundingMode#HALF_UP}) are applied
 * consistently to every computed monetary value — reusing the same
 * {@code NUMERIC(19,4)} precision convention already established for
 * {@code products.entity.Product.price}/{@code taxPercentage} (Phase 9).
 * {@code discountPercentage}/{@code taxPercentage} themselves use
 * {@code NUMERIC(5,2)} (also matching Phase 9's {@code Product.taxPercentage}).
 *
 * <p><b>Discount allocation across line items</b> (project instructions §8):
 * each line item's discount share is computed with the *same formula* used
 * for the quotation-level {@code discountAmount} ({@code amount × percentage
 * / 100}), applied to that line's own subtotal — this is what makes the
 * allocation deterministic and mathematically consistent (every line loses
 * exactly {@code discountPercentage}% of its own subtotal, matching the
 * aggregate {@code discountAmount = subtotal × discountPercentage / 100}
 * exactly, up to the last decimal place of independent per-line rounding).
 * Tax is then calculated per line on the *discounted* line amount, then
 * summed — i.e. tax is calculated after discount, per project instructions §8.
 */
public final class QuotationCalculator {

    public static final int MONEY_SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private QuotationCalculator() {
        // static utility class
    }

    /**
     * Computes and applies every line item's {@code lineSubtotal}/
     * {@code lineTaxAmount} (via {@link QuotationItem#applyCalculatedLineTotals})
     * as a side effect, then returns the quotation-level totals. Never reads
     * a line item's product's *current* price/tax — only the already
     * snapshotted {@code unitPrice}/{@code taxPercentage} stored on each
     * {@link QuotationItem} (project instructions §5).
     */
    public static Totals calculate(List<QuotationItem> items, BigDecimal discountPercentage) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);

        for (QuotationItem item : items) {
            BigDecimal lineSubtotal = item.getQuantity().multiply(item.getUnitPrice())
                    .setScale(MONEY_SCALE, ROUNDING_MODE);
            BigDecimal lineDiscountAmount = percentageOf(lineSubtotal, discountPercentage);
            BigDecimal lineDiscountedAmount = lineSubtotal.subtract(lineDiscountAmount);
            BigDecimal lineTaxAmount = percentageOf(lineDiscountedAmount, item.getTaxPercentage());

            item.applyCalculatedLineTotals(lineSubtotal, lineTaxAmount);

            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTaxAmount);
        }

        BigDecimal discountAmount = percentageOf(subtotal, discountPercentage);
        BigDecimal taxableAmount = subtotal.subtract(discountAmount);
        BigDecimal grandTotal = taxableAmount.add(taxAmount).setScale(MONEY_SCALE, ROUNDING_MODE);

        return new Totals(subtotal, discountAmount, taxAmount, grandTotal);
    }

    private static BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);
    }

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal grandTotal) {
    }
}
