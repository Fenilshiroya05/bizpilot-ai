package com.bizpilot.sales.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.products.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A single line item on a {@link Quotation} (CLAUDE.md §14). References
 * {@code products.entity.Product} (a cross-module entity relationship, same
 * pattern as {@code Quotation.customer}) — always validated to belong to the
 * current organization before being persisted.
 *
 * <p><b>Product snapshot rule</b> (project instructions §5): {@code productNameSnapshot},
 * {@code unitPrice}, and {@code taxPercentage} are copied from the referenced
 * {@link Product} at the moment this item is created/replaced and never
 * re-read from the live product afterward — a quotation is a point-in-time
 * business document, and a later catalog price change must never silently
 * alter an already-created quotation's historical values. All calculations
 * ({@code lineSubtotal}, {@code lineTaxAmount}, and the parent quotation's
 * totals) are always derived from these stored/snapshotted values, never
 * from a fresh product lookup.
 *
 * <p>{@code quantity} is a {@link BigDecimal}, not an integer — CLAUDE.md
 * places no constraint on this, and {@code products.entity.Product.unit} is
 * free text (e.g. "kg", "litre", "meter"), so fractional quantities are a
 * legitimate real-world case this model should support (an implementation
 * decision).
 *
 * <p>No {@code organization_id} column of its own — same "child of a
 * tenant-scoped parent" precedent as {@code CustomerActivity}/{@code LeadActivity}:
 * every access path resolves the parent {@link Quotation} via an
 * organization-scoped lookup first.
 */
@Entity
@Table(name = "quotation_items")
public class QuotationItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false, updatable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, updatable = false)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "tax_percentage", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineSubtotal;

    @Column(name = "line_tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTaxAmount;

    protected QuotationItem() {
        // required by JPA
    }

    public QuotationItem(Quotation quotation, Product product, String productNameSnapshot, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal taxPercentage) {
        this.quotation = quotation;
        this.product = product;
        this.productNameSnapshot = productNameSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.taxPercentage = taxPercentage;
        this.lineSubtotal = BigDecimal.ZERO;
        this.lineTaxAmount = BigDecimal.ZERO;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTaxPercentage() {
        return taxPercentage;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getLineTaxAmount() {
        return lineTaxAmount;
    }

    /** Only ever called by {@code QuotationCalculator} with backend-computed values. */
    public void applyCalculatedLineTotals(BigDecimal lineSubtotal, BigDecimal lineTaxAmount) {
        this.lineSubtotal = lineSubtotal;
        this.lineTaxAmount = lineTaxAmount;
    }
}
