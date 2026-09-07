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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Owns product category business logic. Deliberately minimal — categories
 * have no status/lifecycle field (CLAUDE.md §13 defines one only for
 * products) and delete is a genuine hard delete: {@code products.category_id}
 * is {@code ON DELETE SET NULL} (V7 migration), so deleting a category never
 * deletes or blocks-on products, it only clears their category reference —
 * the simplest, least-destructive behavior for this lightweight metadata
 * entity.
 */
@Service
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;

    public ProductCategoryService(ProductCategoryRepository categoryRepository, TenantContext tenantContext,
                                   OrganizationService organizationService) {
        this.categoryRepository = categoryRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
    }

    @Transactional
    public ProductCategory create(ProductCategoryCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        return categoryRepository.save(new ProductCategory(organization, request.name().trim()));
    }

    @Transactional(readOnly = true)
    public ProductCategory getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<ProductCategory> list(Pageable pageable) {
        return categoryRepository.findByOrganizationId(tenantContext.currentOrganizationId(), pageable);
    }

    @Transactional
    public ProductCategory update(UUID id, ProductCategoryUpdateRequest request) {
        ProductCategory category = findOrThrow(id);
        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw new InvalidProductDataException("name cannot be blank");
            }
            category.setName(request.name().trim());
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(UUID id) {
        ProductCategory category = findOrThrow(id);
        categoryRepository.delete(category);
    }

    private ProductCategory findOrThrow(UUID id) {
        return categoryRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new ProductCategoryNotFoundException(id));
    }
}
