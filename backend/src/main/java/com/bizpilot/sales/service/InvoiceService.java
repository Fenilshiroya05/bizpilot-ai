package com.bizpilot.sales.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.entity.ProductStatus;
import com.bizpilot.products.repository.ProductRepository;
import com.bizpilot.sales.dto.InvoiceCreateRequest;
import com.bizpilot.sales.dto.InvoiceItemRequest;
import com.bizpilot.sales.dto.InvoiceUpdateRequest;
import com.bizpilot.sales.entity.Invoice;
import com.bizpilot.sales.entity.InvoiceItem;
import com.bizpilot.sales.entity.InvoiceStatus;
import com.bizpilot.sales.exception.InvalidCustomerReferenceException;
import com.bizpilot.sales.exception.InvalidInvoiceDataException;
import com.bizpilot.sales.exception.InvalidProductReferenceException;
import com.bizpilot.sales.exception.InvoiceNotEditableException;
import com.bizpilot.sales.exception.InvoiceNotFoundException;
import com.bizpilot.sales.repository.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns all invoice business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input) and
 * every lookup by id is organization-scoped via {@code findByIdAndOrganizationId}
 * — mirrors {@code QuotationService} exactly in structure.
 *
 * <p><b>Immutability (CLAUDE.md §15, project instructions §6/§15/§20/§24)</b>:
 * {@link #update} rejects the request outright with
 * {@link InvoiceNotEditableException} unless the invoice's current status is
 * {@link InvoiceStatus#DRAFT} — there is no carve-out allowing only the
 * {@code status} field to change on a non-DRAFT invoice. This is a deliberate
 * reading of a genuinely ambiguous prompt (CLAUDE.md §15/§20/§24 each
 * independently and unconditionally say "only DRAFT invoices may be
 * updated" / "all other statuses are immutable," while §6's field-level list
 * omits {@code status} and §21 vaguely gestures at "a payment-status update
 * API... if needed" as a *separate*, optional mechanism this phase does not
 * build). The stricter, three-times-repeated reading was chosen over the
 * weaker, single-omission-based one. <b>Practical consequence:</b> once an
 * invoice leaves {@code DRAFT} via this endpoint, its status can only be
 * changed further by cancelling it (transition to {@code CANCELLED} via
 * {@link #cancel}) — there is no supported way in this phase to move e.g.
 * {@code ISSUED -> PARTIALLY_PAID -> PAID} after issuance, since CLAUDE.md
 * §21 explicitly forbids inventing a payment subsystem and no such endpoint
 * is named in CLAUDE.md §15's API list. This is documented as a known,
 * intentional limitation, not an oversight — see docs/security.md and the
 * Phase 11 implementation report.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;

    public InvoiceService(InvoiceRepository invoiceRepository, CustomerRepository customerRepository,
                           ProductRepository productRepository, TenantContext tenantContext,
                           OrganizationService organizationService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
    }

    @Transactional
    public Invoice create(InvoiceCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        Customer customer = resolveCustomer(request.customerId(), organization.getId());

        Invoice invoice = new Invoice(organization, customer, request.dueDate());
        invoice.getItems().addAll(buildItems(invoice, request.items(), organization.getId()));

        recalculate(invoice);
        invoiceRepository.saveAndFlush(invoice);

        return reloadWithItems(invoice.getId(), organization.getId());
    }

    @Transactional(readOnly = true)
    public Invoice getById(UUID id) {
        return invoiceRepository.findByIdAndOrganizationIdWithItems(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Invoice> search(InvoiceSearchCriteria criteria, Pageable pageable) {
        return invoiceRepository.search(
                tenantContext.currentOrganizationId(), criteria.status(), criteria.statuses(), criteria.customerId(),
                criteria.dueDateBefore(), pageable);
    }

    /**
     * "Outstanding" (Phase 17, project instructions §17): {@code ISSUED},
     * {@code PARTIALLY_PAID}, or {@code OVERDUE} — CLAUDE.md §15's own
     * status enum values representing an invoice still owed, in whole or in
     * part. Deliberately a domain-service method, not tool-layer logic
     * (project instructions §12: "tools must not contain business logic
     * that duplicates domain services") — {@code ai.tools.InvoiceTools} only
     * calls this and maps the result; the definition of "outstanding" lives
     * here, the one place any future caller (REST or AI) would look for it.
     */
    private static final List<InvoiceStatus> OUTSTANDING_STATUSES =
            List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    @Transactional(readOnly = true)
    public Page<Invoice> getOutstanding(Pageable pageable) {
        return search(new InvoiceSearchCriteria(null, null, null, OUTSTANDING_STATUSES), pageable);
    }

    @Transactional
    public Invoice update(UUID id, InvoiceUpdateRequest request) {
        UUID organizationId = tenantContext.currentOrganizationId();
        Invoice invoice = invoiceRepository.findByIdAndOrganizationIdWithItems(id, organizationId)
                .orElseThrow(() -> new InvoiceNotFoundException(id));

        if (!invoice.isDraft()) {
            throw new InvoiceNotEditableException(id);
        }

        if (request.status() != null) {
            if (request.status() == InvoiceStatus.CANCELLED) {
                throw new InvalidInvoiceDataException(
                        "Status cannot be set to CANCELLED via update — use the cancel operation instead");
            }
            invoice.setStatus(request.status());
        }
        if (request.customerId() != null) {
            invoice.setCustomer(resolveCustomer(request.customerId(), organizationId));
        }
        if (request.clearDueDate()) {
            invoice.setDueDate(null);
        } else if (request.dueDate() != null) {
            invoice.setDueDate(request.dueDate());
        }
        if (request.items() != null) {
            if (request.items().isEmpty()) {
                throw new InvalidInvoiceDataException("items cannot be empty");
            }
            invoice.getItems().clear();
            invoice.getItems().addAll(buildItems(invoice, request.items(), organizationId));
        }

        recalculate(invoice);
        invoiceRepository.saveAndFlush(invoice);

        return reloadWithItems(id, organizationId);
    }

    /**
     * CLAUDE.md §15 names no "delete" operation distinct from the existing
     * {@code CANCELLED} status value — this transitions the invoice to that
     * status rather than introducing a second lifecycle field, mirroring
     * {@code QuotationService.cancel}. Idempotent: cancelling an
     * already-cancelled invoice is a harmless no-op. Financial history is
     * never physically deleted, and cancellation never recalculates or
     * otherwise mutates the invoice's financial contents.
     */
    @Transactional
    public void cancel(UUID id) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        if (invoice.isCancelled()) {
            return;
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    private List<InvoiceItem> buildItems(Invoice invoice, List<InvoiceItemRequest> requests, UUID organizationId) {
        List<InvoiceItem> items = new ArrayList<>();
        for (InvoiceItemRequest itemRequest : requests) {
            Product product = productRepository.findByIdAndOrganizationId(itemRequest.productId(), organizationId)
                    .orElseThrow(() -> new InvalidProductReferenceException(itemRequest.productId()));
            if (product.getStatus() == ProductStatus.INACTIVE) {
                throw new InvalidProductReferenceException(itemRequest.productId(), "product is inactive");
            }
            // Snapshot rule: name/unitPrice/taxPercentage are copied from the
            // product NOW and never re-read from it again.
            items.add(new InvoiceItem(invoice, product, product.getName(), itemRequest.quantity(),
                    product.getPrice(), product.getTaxPercentage()));
        }
        return items;
    }

    private void recalculate(Invoice invoice) {
        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(invoice.getItems());
        invoice.applyCalculatedTotals(totals.subtotal(), totals.taxAmount(), totals.total());
    }

    private Customer resolveCustomer(UUID customerId, UUID organizationId) {
        Customer customer = customerRepository.findByIdAndOrganizationId(customerId, organizationId)
                .orElseThrow(() -> new InvalidCustomerReferenceException(customerId));
        if (customer.isArchived()) {
            throw new InvalidCustomerReferenceException(customerId, "customer is archived");
        }
        return customer;
    }

    /**
     * Re-fetches with items eagerly joined after a create/update save —
     * mirrors {@code QuotationService.reloadWithItems}.
     */
    private Invoice reloadWithItems(UUID id, UUID organizationId) {
        return invoiceRepository.findByIdAndOrganizationIdWithItems(id, organizationId)
                .orElseThrow(() -> new IllegalStateException("Invoice vanished immediately after save: " + id));
    }
}
