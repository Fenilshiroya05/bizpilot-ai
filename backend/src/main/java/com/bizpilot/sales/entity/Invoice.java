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
 * An invoice (CLAUDE.md §15). Tenant-scoped: every invoice belongs to exactly
 * one {@link Organization}, assigned once at creation and never changed —
 * same convention as {@link Quotation} and every other tenant-scoped entity
 * in this project.
 *
 * <p>{@code customer} is a direct cross-module reference to
 * {@code crm.entity.Customer}, validated tenant-safe (and non-archived) by
 * {@code InvoiceService} before being persisted — never trusted from client
 * input as-is, same pattern as {@link Quotation#customer}.
 *
 * <p><b>No discount.</b> Unlike {@link Quotation}, CLAUDE.md §15 never
 * mentions a discount feature for invoices (a deliberate, confirmed
 * omission, not an oversight) — total is simply {@code subtotal + taxAmount}.
 *
 * <p><b>No quotation reference.</b> CLAUDE.md never describes converting a
 * quotation into an invoice — invoices are created independently, with their
 * own customer and items.
 *
 * <p><b>Immutability.</b> An invoice's financial/identity content (customer,
 * items, product references/snapshots, quantities, due date) is only
 * editable while {@link #status} is {@link InvoiceStatus#DRAFT} — enforced by
 * {@code InvoiceService.update}, not by this entity's setters directly (same
 * separation of concerns as {@link Quotation}, which enforces its own
 * business rules in the service layer). Once an invoice leaves {@code DRAFT},
 * {@code InvoiceService} rejects any further {@code PATCH}. A
 * {@link InvoiceStatus#CANCELLED} invoice is unconditionally immutable.
 *
 * <p><b>Backend-owned monetary fields.</b> {@code subtotal}, {@code taxAmount},
 * and {@code total} are always computed by {@code InvoiceService}/
 * {@code InvoiceCalculator} from the authoritative invoice items — CLAUDE.md
 * §15 ("again, all financial calculations must happen on the backend") is
 * explicit that client-submitted totals are never trusted. Persisted (not
 * purely derived at read-time) so a historical invoice's totals remain
 * stable even if calculation logic is refined later.
 *
 * <p><b>Status vs. cancellation.</b> {@link InvoiceStatus} already contains
 * {@code CANCELLED} — "delete" is modeled as a transition to that existing
 * status value, not a second lifecycle field, mirroring {@link Quotation}.
 */
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
        // required by JPA
    }

    public Invoice(Organization organization, Customer customer, LocalDate dueDate) {
        this.organization = organization;
        this.customer = customer;
        this.status = InvoiceStatus.DRAFT;
        this.dueDate = dueDate;
        this.subtotal = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
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

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    /** Only ever called by {@code InvoiceService} with backend-computed values. */
    public void applyCalculatedTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.total = total;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isCancelled() {
        return status == InvoiceStatus.CANCELLED;
    }
}
