package com.bizpilot.sales.service;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

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
    private Invoice lastSaved;

    @BeforeEach
    void setUp() {
        lastSaved = null;
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(invoiceRepository.saveAndFlush(any(Invoice.class)))
                .thenAnswer(invocation -> {
                    lastSaved = invocation.getArgument(0);
                    return lastSaved;
                });
        lenient().when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(lastSaved));
    }

    private InvoiceService service() {
        return new InvoiceService(invoiceRepository, customerRepository, productRepository, tenantContext,
                organizationService);
    }

    private InvoiceCreateRequest createRequest(UUID customerId, UUID productId, String quantity) {
        return new InvoiceCreateRequest(customerId, null,
                List.of(new InvoiceItemRequest(productId, new BigDecimal(quantity))));
    }

    @Test
    void createValidatesTheCustomerBelongsToTheCurrentOrganization() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(createRequest(customerId, UUID.randomUUID(), "1")))
                .isInstanceOf(InvalidCustomerReferenceException.class);

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createValidatesEveryProductBelongsToTheCurrentOrganization() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(createRequest(customerId, productId, "1")))
                .isInstanceOf(InvalidProductReferenceException.class);

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnArchivedCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer archivedCustomer = new Customer(organization, "Old Customer", null, null, null, null, null, null);
        archivedCustomer.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(archivedCustomer));

        assertThatThrownBy(() -> service().create(createRequest(customerId, UUID.randomUUID(), "1")))
                .isInstanceOf(InvalidCustomerReferenceException.class);

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnInactiveProduct() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product inactiveProduct = new Product(organization, null, "SKU-INACTIVE", "Old Widget", null, "pcs",
                new BigDecimal("50.00"), new BigDecimal("10.00"));
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> service().create(createRequest(customerId, productId, "1")))
                .isInstanceOf(InvalidProductReferenceException.class);

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSnapshotsTheProductsNamePriceAndTaxOntoTheItem() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(customerId), any())).thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndOrganizationId(eq(productId), any())).thenReturn(Optional.of(product));

        Invoice created = service().create(createRequest(customerId, productId, "2"));

        assertThat(created.getItems()).hasSize(1);
        InvoiceItem item = created.getItems().get(0);
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

        // quantity 2 * unitPrice 50.00 = 100.00 subtotal; 10% tax => 10.00 tax; total 110.00.
        Invoice created = service().create(createRequest(customerId, productId, "2"));

        assertThat(created.getSubtotal()).isEqualByComparingTo("100.0000");
        assertThat(created.getTaxAmount()).isEqualByComparingTo("10.0000");
        assertThat(created.getTotal()).isEqualByComparingTo("110.0000");
        assertThat(created.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    void getByIdThrowsNotFoundWhenNoInvoiceMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void updateOfADraftInvoiceReplacesItemsAndRecalculates() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        UUID newProductId = UUID.randomUUID();
        when(productRepository.findByIdAndOrganizationId(eq(newProductId), any())).thenReturn(Optional.of(product));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(
                null, null, false, null, List.of(new InvoiceItemRequest(newProductId, BigDecimal.ONE)));
        Invoice updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getProductNameSnapshot()).isEqualTo("Widget");
        assertThat(updated.getSubtotal()).isEqualByComparingTo("50.0000");
    }

    @Test
    void updateOfADraftInvoiceRejectsReplacingItemsWithAnEmptyList() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(null, null, false, null, List.of());

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidInvoiceDataException.class);
    }

    @Test
    void updateRejectsSettingStatusToCancelledDirectly() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(null, null, false, InvoiceStatus.CANCELLED, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidInvoiceDataException.class);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateValidatesANewCustomerReferenceBelongsToTheCurrentOrganization() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));
        UUID newCustomerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganizationId(eq(newCustomerId), any())).thenReturn(Optional.empty());

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(newCustomerId, null, false, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerReferenceException.class);
    }

    @Test
    void updateRejectsReassigningToAnArchivedCustomer() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));
        UUID newCustomerId = UUID.randomUUID();
        Customer archivedCustomer = new Customer(organization, "Old Customer", null, null, null, null, null, null);
        archivedCustomer.setStatus(CustomerStatus.ARCHIVED);
        when(customerRepository.findByIdAndOrganizationId(eq(newCustomerId), any())).thenReturn(Optional.of(archivedCustomer));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(newCustomerId, null, false, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidCustomerReferenceException.class);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateClearsDueDateWhenExplicitlyRequested() {
        Invoice existing = new Invoice(organization, customer, java.time.LocalDate.now().plusDays(10));
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(null, null, true, null, null);
        Invoice updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getDueDate()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED"})
    void updateIsRejectedForAnyNonDraftInvoice(InvoiceStatus nonDraftStatus) {
        Invoice existing = new Invoice(organization, customer, null);
        existing.setStatus(nonDraftStatus);
        when(invoiceRepository.findByIdAndOrganizationIdWithItems(any(), any())).thenReturn(Optional.of(existing));

        InvoiceUpdateRequest request = new InvoiceUpdateRequest(null, null, true, null, null);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvoiceNotEditableException.class);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelSetsStatusToCancelled() {
        Invoice existing = new Invoice(organization, customer, null);
        when(invoiceRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        Invoice existing = new Invoice(organization, customer, null);
        existing.setStatus(InvoiceStatus.CANCELLED);
        when(invoiceRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void cancelDoesNotRecalculateOrMutateFinancialContents() {
        Invoice existing = new Invoice(organization, customer, null);
        existing.getItems().add(new InvoiceItem(existing, product, "Widget", BigDecimal.ONE,
                new BigDecimal("50.00"), new BigDecimal("10.00")));
        existing.applyCalculatedTotals(new BigDecimal("50.0000"), new BigDecimal("5.0000"), new BigDecimal("55.0000"));
        when(invoiceRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().cancel(UUID.randomUUID());

        assertThat(existing.getSubtotal()).isEqualByComparingTo("50.0000");
        assertThat(existing.getTaxAmount()).isEqualByComparingTo("5.0000");
        assertThat(existing.getTotal()).isEqualByComparingTo("55.0000");
    }

    @Test
    void searchDelegatesToTheRepositoryWithTheCurrentOrganizationId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Invoice> emptyPage = new PageImpl<>(List.of());
        when(invoiceRepository.search(eq(organizationId), any(), any(), any(), eq(pageable))).thenReturn(emptyPage);

        InvoiceSearchCriteria criteria = new InvoiceSearchCriteria(null, null, null);
        service().search(criteria, pageable);

        verify(invoiceRepository).search(eq(organizationId), any(), any(), any(), eq(pageable));
    }
}
