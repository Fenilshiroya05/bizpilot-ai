package com.bizpilot.products.service;

import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.products.dto.ProductCreateRequest;
import com.bizpilot.products.dto.ProductUpdateRequest;
import com.bizpilot.products.entity.Product;
import com.bizpilot.products.entity.ProductCategory;
import com.bizpilot.products.entity.ProductStatus;
import com.bizpilot.products.exception.DuplicateSkuException;
import com.bizpilot.products.exception.InvalidProductCategoryException;
import com.bizpilot.products.exception.InvalidProductDataException;
import com.bizpilot.products.exception.ProductNotFoundException;
import com.bizpilot.products.repository.ProductCategoryRepository;
import com.bizpilot.products.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductService service() {
        return new ProductService(productRepository, categoryRepository, tenantContext, organizationService);
    }

    private ProductCreateRequest createRequest(String sku) {
        return new ProductCreateRequest(sku, "Widget", "A useful widget", "pcs", new BigDecimal("19.99"), null, null);
    }

    @Test
    void createNormalizesSkuToUppercaseAndDefaultsTaxPercentageToZero() {
        when(productRepository.existsByOrganizationIdAndSkuIgnoreCase(any(), any())).thenReturn(false);

        Product created = service().create(createRequest("abc-123"));

        assertThat(created.getSku()).isEqualTo("ABC-123");
        assertThat(created.getTaxPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.getOrganization()).isSameAs(organization);
    }

    @Test
    void createHonorsAnExplicitTaxPercentageWhenSupplied() {
        when(productRepository.existsByOrganizationIdAndSkuIgnoreCase(any(), any())).thenReturn(false);
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-1", "Widget", null, "pcs", new BigDecimal("10.00"), new BigDecimal("18.00"), null);

        Product created = service().create(request);

        assertThat(created.getTaxPercentage()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void createRejectsADuplicateSkuWithinTheSameOrganization() {
        // any() for the organization id: `organization` here is an unsaved
        // fixture entity (no generated id yet), unlike production where
        // OrganizationService.getCurrentOrganization() always returns an
        // already-persisted organization with a real id.
        when(productRepository.existsByOrganizationIdAndSkuIgnoreCase(any(), eq("TAKEN"))).thenReturn(true);

        assertThatThrownBy(() -> service().create(createRequest("taken")))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createValidatesTheCategoryBelongsToTheCurrentOrganization() {
        when(productRepository.existsByOrganizationIdAndSkuIgnoreCase(any(), any())).thenReturn(false);
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndOrganizationId(eq(categoryId), any())).thenReturn(Optional.empty());
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-2", "Widget", null, "pcs", BigDecimal.TEN, null, categoryId);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(InvalidProductCategoryException.class);
    }

    @Test
    void createSucceedsWithAValidCategory() {
        when(productRepository.existsByOrganizationIdAndSkuIgnoreCase(any(), any())).thenReturn(false);
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = new ProductCategory(organization, "Electronics");
        when(categoryRepository.findByIdAndOrganizationId(eq(categoryId), any())).thenReturn(Optional.of(category));
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-3", "Widget", null, "pcs", BigDecimal.TEN, null, categoryId);

        Product created = service().create(request);

        assertThat(created.getCategory()).isSameAs(category);
    }

    @Test
    void getByIdThrowsNotFoundWhenNoProductMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyTheFieldsSuppliedAndLeavesOthersUnchanged() {
        Product existing = new Product(organization, null, "SKU-4", "Original Name", "desc", "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        ProductUpdateRequest request = new ProductUpdateRequest(
                null, "New Name", null, null, null, null, null, null, false);
        Product updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getSku()).isEqualTo("SKU-4");
        assertThat(updated.getPrice()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void updateRejectsBlankName() {
        Product existing = new Product(organization, null, "SKU-5", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        ProductUpdateRequest request = new ProductUpdateRequest(
                null, "   ", null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), request))
                .isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void updateAllowsModifyingAnInactiveProductUnlikeArchivedCustomersOrLeads() {
        Product inactive = new Product(organization, null, "SKU-6", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        inactive.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(inactive));

        ProductUpdateRequest request = new ProductUpdateRequest(
                null, "Reactivated Name", null, null, null, null, ProductStatus.ACTIVE, null, false);
        Product updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getName()).isEqualTo("Reactivated Name");
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void updateClearsCategoryWhenExplicitlyRequested() {
        ProductCategory category = new ProductCategory(organization, "Electronics");
        Product existing = new Product(organization, category, "SKU-7", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        ProductUpdateRequest request = new ProductUpdateRequest(
                null, null, null, null, null, null, null, null, true);
        Product updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getCategory()).isNull();
    }

    @Test
    void updateGivesClearCategoryPrecedenceOverASimultaneouslySuppliedCategoryId() {
        ProductCategory originalCategory = new ProductCategory(organization, "Electronics");
        Product existing = new Product(organization, originalCategory, "SKU-CLEAR-PRECEDENCE", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        UUID otherCategoryId = UUID.randomUUID();

        // clearCategory=true alongside a non-null categoryId — clearing must win.
        ProductUpdateRequest request = new ProductUpdateRequest(
                null, null, null, null, null, null, null, otherCategoryId, true);
        Product updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getCategory()).isNull();
        verify(categoryRepository, never()).findByIdAndOrganizationId(eq(otherCategoryId), any());
    }

    @Test
    void updateWithNeitherCategoryIdNorClearCategoryLeavesAnExistingCategoryUnchanged() {
        ProductCategory existingCategory = new ProductCategory(organization, "Electronics");
        Product existing = new Product(organization, existingCategory, "SKU-CAT-UNCHANGED", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        ProductUpdateRequest request = new ProductUpdateRequest(
                null, "New Name", null, null, null, null, null, null, false);
        Product updated = service().update(UUID.randomUUID(), request);

        assertThat(updated.getCategory()).isSameAs(existingCategory);
    }

    @Test
    void deleteTransitionsToInactiveRatherThanRemovingTheRow() {
        Product existing = new Product(organization, null, "SKU-8", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().delete(UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void deleteIsIdempotentWhenAlreadyInactive() {
        Product alreadyInactive = new Product(organization, null, "SKU-9", "Name", null, "pcs",
                BigDecimal.TEN, BigDecimal.ZERO);
        alreadyInactive.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(alreadyInactive));

        service().delete(UUID.randomUUID());

        assertThat(alreadyInactive.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }
}
