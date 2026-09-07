package com.bizpilot.products.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A catalog product (CLAUDE.md §13). Tenant-scoped: every product belongs to
 * exactly one {@link Organization}, assigned once at creation and never
 * changed — same convention as {@code identity.entity.User},
 * {@code crm.entity.Customer}, and {@code sales.entity.Lead}.
 *
 * <p>Fields mirror CLAUDE.md §13 exactly: SKU, name, description, unit,
 * price, tax percentage, active/inactive status — no inventory/stock,
 * warehouse, supplier, purchase price, or discount fields, none of which
 * CLAUDE.md mentions for this phase.
 *
 * <p>{@code category} is optional (zero-or-one, never many-to-many —
 * project instructions §3) and, when set, is always validated by
 * {@code ProductService} to belong to the same organization before being
 * persisted — never trusted from client input as-is.
 *
 * <p><b>No separate archive mechanism.</b> Unlike {@code Customer}/{@code Lead},
 * CLAUDE.md §13 names no "delete/archive" feature for products — only "CRUD."
 * {@link ProductStatus#INACTIVE} already represents "intentionally
 * unavailable," so {@code ProductService}'s delete operation transitions a
 * product to {@code INACTIVE} rather than introducing a second,
 * redundant lifecycle field. Unlike Customer/Lead archival, this is fully
 * reversible via a subsequent update back to {@code ACTIVE}.
 */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ProductStatus status;

    protected Product() {
        // required by JPA
    }

    public Product(Organization organization, ProductCategory category, String sku, String name, String description,
                   String unit, BigDecimal price, BigDecimal taxPercentage) {
        this.organization = organization;
        this.category = category;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.price = price;
        this.taxPercentage = taxPercentage;
        this.status = ProductStatus.ACTIVE;
    }

    public Organization getOrganization() {
        return organization;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getTaxPercentage() {
        return taxPercentage;
    }

    public void setTaxPercentage(BigDecimal taxPercentage) {
        this.taxPercentage = taxPercentage;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }
}
