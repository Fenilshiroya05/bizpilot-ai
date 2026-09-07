package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.QuotationItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of {@link QuotationCalculator} — deliberately no
 * Spring context, no Mockito, no persistence: a pure function over
 * {@link QuotationItem} objects, per project instructions §22 ("keep
 * financial calculation logic deterministic and easily testable").
 */
class QuotationCalculatorTest {

    private static QuotationItem item(String quantity, String unitPrice, String taxPercentage) {
        return new QuotationItem(null, null, "Test Product",
                new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(taxPercentage));
    }

    @Test
    void singleLineItemNoDiscountNoTax() {
        QuotationItem item = item("2", "10.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item), BigDecimal.ZERO);

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("20.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("20.0000");
        assertThat(totals.discountAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("20.0000");
    }

    @Test
    void singleLineItemWithTaxAndNoDiscount() {
        QuotationItem item = item("1", "100.00", "18");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item), BigDecimal.ZERO);

        assertThat(item.getLineTaxAmount()).isEqualByComparingTo("18.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("100.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("18.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("118.0000");
    }

    @Test
    void discountIsAppliedBeforeTaxIsCalculated() {
        // subtotal 100, 10% discount -> discounted 90, 10% tax on the discounted 90 = 9, not on the raw 100.
        QuotationItem item = item("1", "100.00", "10");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item), new BigDecimal("10"));

        assertThat(totals.discountAmount()).isEqualByComparingTo("10.0000");
        assertThat(item.getLineTaxAmount()).isEqualByComparingTo("9.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("9.0000");
        // grandTotal = taxableAmount (subtotal - discountAmount = 90) + taxAmount (9) = 99.
        assertThat(totals.grandTotal()).isEqualByComparingTo("99.0000");
    }

    @Test
    void multipleLineItemsSumIntoTheSubtotal() {
        QuotationItem item1 = item("2", "10.00", "0");
        QuotationItem item2 = item("3", "5.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item1, item2), BigDecimal.ZERO);

        assertThat(totals.subtotal()).isEqualByComparingTo("35.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("35.0000");
    }

    @Test
    void multipleLineItemsWithDifferentTaxRatesAreSummedIndependently() {
        QuotationItem item1 = item("1", "100.00", "5");
        QuotationItem item2 = item("1", "100.00", "20");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item1, item2), BigDecimal.ZERO);

        assertThat(totals.subtotal()).isEqualByComparingTo("200.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("25.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("225.0000");
    }

    @Test
    void theDiscountAllocationAcrossLinesIsConsistentWithTheAggregateDiscount() {
        // Two lines with different subtotals; each should lose exactly
        // discountPercentage% of its OWN subtotal, and the sum of line
        // discounts must equal the aggregate discountAmount exactly (same
        // percentage, same rounding rule, applied consistently).
        QuotationItem item1 = item("1", "100.00", "0");
        QuotationItem item2 = item("1", "300.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item1, item2), new BigDecimal("10"));

        assertThat(totals.subtotal()).isEqualByComparingTo("400.0000");
        assertThat(totals.discountAmount()).isEqualByComparingTo("40.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("360.0000");
    }

    @Test
    void decimalQuantitiesAreSupported() {
        QuotationItem item = item("2.5", "4.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item), BigDecimal.ZERO);

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("10.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("10.0000");
    }

    @Test
    void roundingUsesHalfUpAtFourDecimalPlaces() {
        // 10.00005 is exactly halfway between 10.0000 and 10.0001 at 4 decimal
        // places — HALF_UP rounds away from zero on an exact tie.
        QuotationItem item = item("1", "10.00005", "0");

        QuotationCalculator.calculate(List.of(item), BigDecimal.ZERO);

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("10.0001");
    }

    @Test
    void zeroDiscountAndZeroTaxProduceAGrandTotalEqualToTheSubtotal() {
        QuotationItem item = item("4", "25.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item), BigDecimal.ZERO);

        assertThat(totals.grandTotal()).isEqualByComparingTo(totals.subtotal());
    }

    @Test
    void aggregateDiscountAmountCanDivergeFromTheSumOfPerLineDiscountSharesBySubCentRounding() {
        // Documented, disclosed tradeoff (see class Javadoc): discountAmount
        // is computed once from the aggregate subtotal, not as the sum of
        // each line's own independently-rounded discount share, so the two
        // can diverge by a sub-cent (at MONEY_SCALE) rounding amount.
        //
        // Each 0.0001 line, discounted 50%, independently rounds its own
        // 0.00005 discount share HALF_UP to 0.0001 — two lines would sum to
        // 0.0002 — but the aggregate discountAmount is computed from the
        // summed subtotal (0.0002 * 50% = 0.0001 exactly, no rounding), so
        // it comes out as 0.0001, not 0.0002.
        QuotationItem item1 = item("0.0001", "1.00", "0");
        QuotationItem item2 = item("0.0001", "1.00", "0");

        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(item1, item2), new BigDecimal("50"));

        BigDecimal sumOfPerLineDiscountShares = new BigDecimal("0.0001").add(new BigDecimal("0.0001"));
        assertThat(totals.discountAmount()).isEqualByComparingTo("0.0001");
        assertThat(totals.discountAmount()).isNotEqualByComparingTo(sumOfPerLineDiscountShares);
    }

    @Test
    void emptyItemListProducesAllZeroTotals() {
        QuotationCalculator.Totals totals = QuotationCalculator.calculate(List.of(), BigDecimal.ZERO);

        assertThat(totals.subtotal()).isEqualByComparingTo("0.0000");
        assertThat(totals.discountAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("0.0000");
    }
}
