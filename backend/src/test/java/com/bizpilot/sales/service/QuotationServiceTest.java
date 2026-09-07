package com.bizpilot.sales.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.repository.CustomerRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.repository.ProductRepository;
import com.bizpilot.sales.dto.QuotationCreateRequest;
import com.bizpilot.sales.dto.QuotationItemRequest;
import com.bizpilot.sales.dto.QuotationUpdateRequest;
import com.bizpilot.sales.entity.Quotation;
import com.bizpilot.sales.entity.QuotationStatus;
import com.bizpilot.sales.exception.InvalidCustomerReferenceException;
import com.bizpilot.sales.exception.InvalidProductReferenceException;
import com.bizpilot.sales.exception.InvalidQuotationDataException;
import com.bizpilot.sales.exception.QuotationNotFoundException;
import com.bizpilot.sales.repository.QuotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");
    private final Customer customer = new Customer(organization, "Jane Customer", null, null, null, null, null, null);
    private final Product product = new Product(organization, null, "SKU-1", "Widget", null, "pcs",
            new BigDecimal("50.00"), new BigDecimal("10.00"));

    /** Simulates the real repository: {@code reloadWithItems} re-fetches whatever was just saved. */
    private Quotation lastSaved;

    @BeforeEach
    void setUp() {
        lastSaved = null;
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(quotationRepository.saveAndFlush(any(Quotation.class)))
                .thenAnswer(invocation -> {
                    lastSaved = invocation.getArgument(0);
                    return lastSaved;
                });
        lenient().when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(lastSaved));
    }

    private QuotationService service() {
        return new QuotationService(quotationRepository, customerRepository, productRepository, tenantContext,
                organizationService);
    }

    private QuotationCreateRequest createRequest(UUID customerId, UUID productId, String quantity) {
        return new QuotationCreateRequest(customerId, null, null,
                List.of(new QuotationItemRequest(productId, new BigDecimal(quantity))));
    }

    @Test
    void createValidatesTheCustomerBelongsToTheCurrentOrganization() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(createRequest(customerId, UUID.randomUUID(), "1")))
                .isInstanceOf(InvalidCustomerReferenceException.class);

        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createValidatesEveryProductBelongsToTheCurrentOrganization() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(createRequest(customerId, productId, "1")))
                .isInstanceOf(InvalidProductReferenceException.class);

        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnArchivedCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer archivedCustomer = new Customer(organization, "Old Customer", null, null, null, null, null, null);
        archivedCustomer.setStatus(com.bizpilot.crm.entity.CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(archivedCustomer));

        assertThatThrownBy(() -> service().create(createRequest(customerId, UUID.randomUUID(), "1")))
                .isInstanceOf(InvalidCustomerReferenceException.class);

        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnInactiveProduct() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product inactiveProduct = new Product(organization, null, "SKU-INACTIVE", "Old Widget", null, "pcs",
                new BigDecimal("50.00"), new BigDecimal("10.00"));
        inactiveProduct.setStatus(com.bizpilot.products.entity.ProductStatus.INACTIVE);
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> service().create(createRequest(customerId, productId, "1")))
                .isInstanceOf(InvalidProductReferenceException.class);

        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSnapshotsTheProductsNamePriceAndTaxOntoTheItem() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(product));

        Quotation created = service().create(createRequest(customerId, productId, "2"));

        assertThat(created.getItems()).hasSize(1);
        var item = created.getItems().get(0);
        assertThat(item.getProductNameSnapshot()).isEqualTo("Widget");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(item.getTaxPercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    void createRecalculatesTotalsFromTheAuthoritativeItemsNotFromAnyClientInput() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(product));

        // quantity 2 * unitPrice 50.00 = 100.00 subtotal; 10% tax => 10.00 tax; grandTotal 110.00.
        Quotation created = service().create(createRequest(customerId, productId, "2"));

        assertThat(created.getSubtotal()).isEqualByComparingTo("100.0000");
        assertThat(created.getTaxAmount()).isEqualByComparingTo("10.0000");
        assertThat(created.getGrandTotal()).isEqualByComparingTo("110.0000");
        assertThat(created.getStatus()).isEqualTo(QuotationStatus.DRAFT);
    }

    @Test
    void createDefaultsDiscountPercentageToZeroWhenOmitted() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(product));

        Quotation created = service().create(createRequest(customerId, productId, "1"));

        assertThat(created.getDiscountPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getByIdThrowsNotFoundWhenNoQuotationMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(quotationRepository.findByIdAndOrganizationIdWithItems(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(QuotationNotFoundException.class);
    }

    @Test
    void updateRejectsSettingStatusToCancelledDirectly() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        QuotationUpdateRequest request = new QuotationUpdateRequest(
                null, null, false, null, QuotationStatus.CANCELLED, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidQuotationDataException.class);
        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsReplacingItemsWithAnEmptyList() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        QuotationUpdateRequest request = new QuotationUpdateRequest(
                null, null, false, null, null, List.of());

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidQuotationDataException.class);
    }

    @Test
    void updateReplacesTheEntireItemCollectionWhenItemsAreSupplied() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        UUID oldProductId = UUID.randomUUID();
        Product oldProduct = new Product(organization, null, "OLD-SKU", "Old Product", null, "pcs",
                new BigDecimal("1.00"), BigDecimal.ZERO);
        existing.getItems().add(new com.bizpilot.sales.entity.QuotationItem(
                existing, oldProduct, "Old Product", BigDecimal.ONE, new BigDecimal("1.00"), BigDecimal.ZERO));
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        UUID newProductId = UUID.randomUUID();
        when(productRepository.findByIdAndOrganizationId(eq(newProductId), any())).thenReturn(Optional.of(product));

        QuotationUpdateRequest request = new QuotationUpdateRequest(
                null, null, false, null, null, List.of(new QuotationItemRequest(newProductId, BigDecimal.ONE)));
        Quotation updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getProductNameSnapshot()).isEqualTo("Widget");
    }

    @Test
    void updateClearsValidUntilWhenExplicitlyRequested() {
        Quotation existing = new Quotation(organization, customer, LocalDate.now().plusDays(10), BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        QuotationUpdateRequest request = new QuotationUpdateRequest(null, null, true, null, null, null);
        Quotation updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getValidUntil()).isNull();
    }

    @Test
    void updateValidatesANewCustomerReferenceBelongsToTheCurrentOrganization() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));
        UUID newCustomerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(newCustomerId), any())).thenReturn(Optional.empty());

        QuotationUpdateRequest request = new QuotationUpdateRequest(newCustomerId, null, false, null, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerReferenceException.class);
    }

    @Test
    void updateRejectsReassigningToAnArchivedCustomer() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));
        UUID newCustomerId = UUID.randomUUID();
        Customer archivedCustomer = new Customer(organization, "Old Customer", null, null, null, null, null, null);
        archivedCustomer.setStatus(com.bizpilot.crm.entity.CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(eq(newCustomerId), any())).thenReturn(Optional.of(archivedCustomer));

        QuotationUpdateRequest request = new QuotationUpdateRequest(newCustomerId, null, false, null, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerReferenceException.class);
        verify(quotationRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelSetsStatusToCancelled() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        when(quotationRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(QuotationStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        Quotation existing = new Quotation(organization, customer, null, BigDecimal.ZERO);
        existing.setStatus(QuotationStatus.CANCELLED);
        when(quotationRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        verify(quotationRepository, never()).save(any());
    }

    @Test
    void searchDelegatesToTheRepositoryWithTheCurrentOrganizationId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Quotation> emptyPage = new PageImpl<>(List.of());
        when(quotationRepository.search(eq(organizationId), any(), any(), any(), eq(pageable))).thenReturn(emptyPage);

        QuotationSearchCriteria criteria = new QuotationSearchCriteria(null, null, null);
        service().search(criteria, pageable);

        verify(quotationRepository).search(eq(organizationId), any(), any(), any(), eq(pageable));
    }
}
