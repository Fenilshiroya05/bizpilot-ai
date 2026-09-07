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
 * A single line item on an {@link Invoice} (CLAUDE.md §15). References
 * {@code products.entity.Product} (a cross-module entity relationship, same
 * pattern as {@code QuotationItem.product}) — always validated to belong to
 * the current organization (and be active) before being persisted.
 *
 * <p><b>Product snapshot rule</b> (mirrors {@code QuotationItem}):
 * {@code productNameSnapshot}, {@code unitPrice}, and {@code taxPercentage}
 * are copied from the referenced {@link Product} at the moment this item is
 * created/replaced and never re-read from the live product afterward. Once
 * the parent {@link Invoice} leaves {@link InvoiceStatus#DRAFT},
 * {@code InvoiceService} rejects any further item replacement entirely, so
 * these snapshot values are permanently frozen for the life of the invoice.
 *
 * <p>No {@code organization_id} column of its own — same "child of a
 * tenant-scoped parent" precedent as {@code QuotationItem}: every access path
 * resolves the parent {@link Invoice} via an organization-scoped lookup first.
 */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

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

    protected InvoiceItem() {
        // required by JPA
    }

    public InvoiceItem(Invoice invoice, Product product, String productNameSnapshot, BigDecimal quantity,
                        BigDecimal unitPrice, BigDecimal taxPercentage) {
        this.invoice = invoice;
        this.product = product;
        this.productNameSnapshot = productNameSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.taxPercentage = taxPercentage;
        this.lineSubtotal = BigDecimal.ZERO;
        this.lineTaxAmount = BigDecimal.ZERO;
    }

    public Invoice getInvoice() {
        return invoice;
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

    /** Only ever called by {@code InvoiceCalculator} with backend-computed values. */
    public void applyCalculatedLineTotals(BigDecimal lineSubtotal, BigDecimal lineTaxAmount) {
        this.lineSubtotal = lineSubtotal;
        this.lineTaxAmount = lineTaxAmount;
    }
}
