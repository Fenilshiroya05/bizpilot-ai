package com.bizpilot.sales.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.crm.entity.Customer;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A quotation (CLAUDE.md §14). Tenant-scoped: every quotation belongs to
 * exactly one {@link Organization}, assigned once at creation and never
 * changed — same convention as every other tenant-scoped entity in this
 * project.
 *
 * <p>{@code customer} is a direct reference to {@code crm.entity.Customer}
 * (a cross-module entity relationship) — the same established pattern as
 * {@code identity.entity.User} referencing {@code organization.entity.Organization}:
 * a genuine data relationship expressed as a JPA association, not a
 * repository-reaching-into-another-module violation. It is always validated
 * to belong to the current organization by {@code QuotationService} before
 * being persisted — never trusted from client input as-is (project
 * instructions §6).
 *
 * <p><b>Backend-owned monetary fields.</b> {@code subtotal}, {@code discountAmount},
 * {@code taxAmount}, and {@code grandTotal} are always computed by
 * {@code QuotationService}/{@code QuotationCalculator} from the authoritative
 * quotation items and {@code discountPercentage} — CLAUDE.md §14 and §41 are
 * explicit that the client's submitted totals (if any) are never trusted.
 * These fields are still persisted (not purely derived at read-time) so a
 * quotation's historical totals remain stable even if calculation logic is
 * refined later, and so they can be queried/reported on directly.
 *
 * <p><b>Status vs. cancellation.</b> Unlike {@code Lead} (whose status enum
 * has no cancellation-equivalent value, requiring a separate {@code archivedAt}
 * field), {@link QuotationStatus} already contains {@code CANCELLED} — so
 * "delete" is modeled as a transition to that existing status value, not a
 * second lifecycle field (project instructions §12).
 */
@Entity
@Table(name = "quotations")
public class Quotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuotationStatus status;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<QuotationItem> items = new ArrayList<>();

    protected Quotation() {
        // required by JPA
    }

    public Quotation(Organization organization, Customer customer, LocalDate validUntil,
                      BigDecimal discountPercentage) {
        this.organization = organization;
        this.customer = customer;
        this.status = QuotationStatus.DRAFT;
        this.validUntil = validUntil;
        this.discountPercentage = discountPercentage;
        this.subtotal = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.grandTotal = BigDecimal.ZERO;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public QuotationStatus getStatus() {
        return status;
    }

    public void setStatus(QuotationStatus status) {
        this.status = status;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    /** Only ever called by {@code QuotationService} with backend-computed values (project instructions §9). */
    public void applyCalculatedTotals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
                                       BigDecimal grandTotal) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.grandTotal = grandTotal;
    }

    public List<QuotationItem> getItems() {
        return items;
    }

    public boolean isCancelled() {
        return status == QuotationStatus.CANCELLED;
    }
}
