package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.InvoiceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of {@link InvoiceCalculator} — pure function over
 * {@link InvoiceItem} objects, mirroring {@code QuotationCalculatorTest}'s
 * structure. No discount cases here — CLAUDE.md §15 never mentions an
 * invoice discount, so {@code InvoiceCalculator} has no discount step at all.
 */
class InvoiceCalculatorTest {

    private static InvoiceItem item(String quantity, String unitPrice, String taxPercentage) {
        return new InvoiceItem(null, null, "Test Product",
                new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(taxPercentage));
    }

    @Test
    void singleLineItemNoTax() {
        InvoiceItem item = item("2", "10.00", "0");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item));

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("20.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("20.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.total()).isEqualByComparingTo("20.0000");
    }

    @Test
    void singleLineItemWithTax() {
        InvoiceItem item = item("1", "100.00", "18");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item));

        assertThat(item.getLineTaxAmount()).isEqualByComparingTo("18.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("100.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("18.0000");
        assertThat(totals.total()).isEqualByComparingTo("118.0000");
    }

    @Test
    void multipleLineItemsSumIntoTheSubtotal() {
        InvoiceItem item1 = item("2", "10.00", "0");
        InvoiceItem item2 = item("3", "5.00", "0");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item1, item2));

        assertThat(totals.subtotal()).isEqualByComparingTo("35.0000");
        assertThat(totals.total()).isEqualByComparingTo("35.0000");
    }

    @Test
    void multipleLineItemsWithDifferentTaxRatesAreSummedIndependently() {
        InvoiceItem item1 = item("1", "100.00", "5");
        InvoiceItem item2 = item("1", "100.00", "20");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item1, item2));

        assertThat(totals.subtotal()).isEqualByComparingTo("200.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("25.0000");
        assertThat(totals.total()).isEqualByComparingTo("225.0000");
    }

    @Test
    void decimalQuantitiesAreSupported() {
        InvoiceItem item = item("2.5", "4.00", "0");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item));

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("10.0000");
        assertThat(totals.subtotal()).isEqualByComparingTo("10.0000");
    }

    @Test
    void roundingUsesHalfUpAtFourDecimalPlaces() {
        // 10.00005 is exactly halfway between 10.0000 and 10.0001 at 4 decimal
        // places — HALF_UP rounds away from zero on an exact tie.
        InvoiceItem item = item("1", "10.00005", "0");

        InvoiceCalculator.calculate(List.of(item));

        assertThat(item.getLineSubtotal()).isEqualByComparingTo("10.0001");
    }

    @Test
    void zeroTaxProducesATotalEqualToTheSubtotal() {
        InvoiceItem item = item("4", "25.00", "0");

        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of(item));

        assertThat(totals.total()).isEqualByComparingTo(totals.subtotal());
    }

    @Test
    void emptyItemListProducesAllZeroTotals() {
        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(List.of());

        assertThat(totals.subtotal()).isEqualByComparingTo("0.0000");
        assertThat(totals.taxAmount()).isEqualByComparingTo("0.0000");
        assertThat(totals.total()).isEqualByComparingTo("0.0000");
    }

    @Test
    void calculationIsDeterministicAcrossRepeatedInvocationsOnTheSameInputs() {
        InvoiceItem item1 = item("3", "7.25", "12.5");
        InvoiceItem item2 = item("1.5", "19.99", "5");

        InvoiceCalculator.Totals first = InvoiceCalculator.calculate(List.of(item1, item2));
        InvoiceCalculator.Totals second = InvoiceCalculator.calculate(List.of(item1, item2));

        assertThat(first).isEqualTo(second);
    }
}
