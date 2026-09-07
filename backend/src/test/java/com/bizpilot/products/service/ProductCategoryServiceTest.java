package com.bizpilot.products.service;

import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.products.dto.ProductCategoryCreateRequest;
import com.bizpilot.products.dto.ProductCategoryUpdateRequest;
import com.bizpilot.products.entity.ProductCategory;
import com.bizpilot.products.exception.InvalidProductDataException;
import com.bizpilot.products.exception.ProductCategoryNotFoundException;
import com.bizpilot.products.repository.ProductCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

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
        lenient().when(categoryRepository.save(any(ProductCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductCategoryService service() {
        return new ProductCategoryService(categoryRepository, tenantContext, organizationService);
    }

    @Test
    void createTrimsTheNameAndAssignsTheCurrentOrganization() {
        ProductCategory created = service().create(new ProductCategoryCreateRequest("  Electronics  "));

        assertThat(created.getName()).isEqualTo("Electronics");
        assertThat(created.getOrganization()).isSameAs(organization);
    }

    @Test
    void getByIdThrowsNotFoundWhenNoCategoryMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(ProductCategoryNotFoundException.class);
    }

    @Test
    void updateAppliesTheSuppliedName() {
        ProductCategory existing = new ProductCategory(organization, "Original");
        when(categoryRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        ProductCategory updated = service().update(UUID.randomUUID(), new ProductCategoryUpdateRequest("Updated"));

        assertThat(updated.getName()).isEqualTo("Updated");
    }

    @Test
    void updateRejectsBlankName() {
        ProductCategory existing = new ProductCategory(organization, "Original");
        when(categoryRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().update(UUID.randomUUID(), new ProductCategoryUpdateRequest("   ")))
                .isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void deleteRemovesTheCategoryRowAsAGenuineHardDelete() {
        ProductCategory existing = new ProductCategory(organization, "Original");
        when(categoryRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().delete(UUID.randomUUID());

        verify(categoryRepository).delete(existing);
    }
}
