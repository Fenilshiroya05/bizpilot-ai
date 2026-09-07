package com.bizpilot.sales.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.entity.ProductStatus;
import com.bizpilot.products.repository.ProductRepository;
import com.bizpilot.sales.dto.QuotationCreateRequest;
import com.bizpilot.sales.dto.QuotationItemRequest;
import com.bizpilot.sales.dto.QuotationUpdateRequest;
import com.bizpilot.sales.entity.Quotation;
import com.bizpilot.sales.entity.QuotationItem;
import com.bizpilot.sales.entity.QuotationStatus;
import com.bizpilot.sales.exception.InvalidCustomerReferenceException;
import com.bizpilot.sales.exception.InvalidProductReferenceException;
import com.bizpilot.sales.exception.InvalidQuotationDataException;
import com.bizpilot.sales.exception.QuotationNotFoundException;
import com.bizpilot.sales.repository.QuotationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns all quotation business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input, project
 * instructions §17) and every lookup by id is organization-scoped via
 * {@code findByIdAndOrganizationId} — there is no code path here that fetches
 * a quotation, customer reference, or product reference by id alone.
 *
 * <p>Reaches directly into {@code crm.repository.CustomerRepository} and
 * {@code products.repository.ProductRepository} (cross-module repository
 * access) for the sole purpose of the tenant-safe existence/ownership check
 * (project instructions §6) — the same established precedent as
 * {@code LeadService} reaching into {@code identity.repository.UserRepository}
 * for assignee validation (Phase 8): a narrow, single-method reuse, not a
 * business-logic duplication.
 */
@Service
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;

    public QuotationService(QuotationRepository quotationRepository, CustomerRepository customerRepository,
                             ProductRepository productRepository, TenantContext tenantContext,
                             OrganizationService organizationService) {
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
    }

    @Transactional
    public Quotation create(QuotationCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        Customer customer = resolveCustomer(request.customerId(), organization.getId());
        BigDecimal discountPercentage = request.discountPercentage() != null
                ? request.discountPercentage() : BigDecimal.ZERO;

        Quotation quotation = new Quotation(organization, customer, request.validUntil(), discountPercentage);
        quotation.getItems().addAll(buildItems(quotation, request.items(), organization.getId()));

        recalculate(quotation);
        quotationRepository.saveAndFlush(quotation);

        return reloadWithItems(quotation.getId(), organization.getId());
    }

    @Transactional(readOnly = true)
    public Quotation getById(UUID id) {
        return quotationRepository.findByIdAndOrganizationIdWithItems(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new QuotationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Quotation> search(QuotationSearchCriteria criteria, Pageable pageable) {
        return quotationRepository.search(
                tenantContext.currentOrganizationId(), criteria.status(), criteria.customerId(),
                criteria.validUntilBefore(), pageable);
    }

    @Transactional
    public Quotation update(UUID id, QuotationUpdateRequest request) {
        UUID organizationId = tenantContext.currentOrganizationId();
        Quotation quotation = quotationRepository.findByIdAndOrganizationIdWithItems(id, organizationId)
                .orElseThrow(() -> new QuotationNotFoundException(id));

        if (request.status() != null) {
            if (request.status() == QuotationStatus.CANCELLED) {
                throw new InvalidQuotationDataException(
                        "Status cannot be set to CANCELLED via update — use the cancel operation instead");
            }
            quotation.setStatus(request.status());
        }
        if (request.customerId() != null) {
            quotation.setCustomer(resolveCustomer(request.customerId(), organizationId));
        }
        if (request.clearValidUntil()) {
            quotation.setValidUntil(null);
        } else if (request.validUntil() != null) {
            quotation.setValidUntil(request.validUntil());
        }
        if (request.discountPercentage() != null) {
            quotation.setDiscountPercentage(request.discountPercentage());
        }
        if (request.items() != null) {
            if (request.items().isEmpty()) {
                throw new InvalidQuotationDataException("items cannot be empty");
            }
            quotation.getItems().clear();
            quotation.getItems().addAll(buildItems(quotation, request.items(), organizationId));
        }

        recalculate(quotation);
        quotationRepository.saveAndFlush(quotation);

        return reloadWithItems(id, organizationId);
    }

    /**
     * CLAUDE.md §14 names no "delete" operation distinct from the existing
     * {@code CANCELLED} status value — this transitions the quotation to
     * that status rather than introducing a second lifecycle field (project
     * instructions §12). Idempotent: cancelling an already-cancelled
     * quotation is a harmless no-op. Financial history is never physically
     * deleted.
     */
    @Transactional
    public void cancel(UUID id) {
        Quotation quotation = quotationRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new QuotationNotFoundException(id));
        if (quotation.isCancelled()) {
            return;
        }
        quotation.setStatus(QuotationStatus.CANCELLED);
        quotationRepository.save(quotation);
    }

    private List<QuotationItem> buildItems(Quotation quotation, List<QuotationItemRequest> requests,
                                            UUID organizationId) {
        List<QuotationItem> items = new ArrayList<>();
        for (QuotationItemRequest itemRequest : requests) {
            Product product = productRepository.findByIdAndOrganizationId(itemRequest.productId(), organizationId)
                    .orElseThrow(() -> new InvalidProductReferenceException(itemRequest.productId()));
            if (product.getStatus() == ProductStatus.INACTIVE) {
                throw new InvalidProductReferenceException(itemRequest.productId(), "product is inactive");
            }
            // Snapshot rule (project instructions §5): name/unitPrice/taxPercentage
            // are copied from the product NOW and never re-read from it again.
            items.add(new QuotationItem(quotation, product, product.getName(), itemRequest.quantity(),
                    product.getPrice(), product.getTaxPercentage()));
        }
        return items;
    }

    private void recalculate(Quotation quotation) {
        QuotationCalculator.Totals totals = QuotationCalculator.calculate(
                quotation.getItems(), quotation.getDiscountPercentage());
        quotation.applyCalculatedTotals(
                totals.subtotal(), totals.discountAmount(), totals.taxAmount(), totals.grandTotal());
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
     * Re-fetches with items eagerly joined after a create/update save — a
     * deliberate, small extra query that removes any dependence on subtle
     * Hibernate new-entity-vs-loaded-entity lazy-collection semantics, so
     * the returned {@code Quotation} is always safe for
     * {@code QuotationMapper.toResponse}/PDF generation to read {@code items}
     * from after this transaction closes.
     */
    private Quotation reloadWithItems(UUID id, UUID organizationId) {
        return quotationRepository.findByIdAndOrganizationIdWithItems(id, organizationId)
                .orElseThrow(() -> new IllegalStateException("Quotation vanished immediately after save: " + id));
    }
}
