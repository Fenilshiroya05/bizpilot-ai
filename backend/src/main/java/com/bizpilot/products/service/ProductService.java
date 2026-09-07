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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns all product business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input, project
 * instructions §12) and every lookup by id is organization-scoped via
 * {@code findByIdAndOrganizationId} — there is no code path here that fetches
 * a product (or validates a category reference) by id alone.
 *
 * <p>Unlike {@code CustomerService}/{@code LeadService}, there is no
 * archived-record modification guard: {@link ProductStatus#INACTIVE} is a
 * normal, freely-editable business state (not a soft-delete lock), so
 * updating or reactivating an inactive product is always allowed.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;

    public ProductService(ProductRepository productRepository, ProductCategoryRepository categoryRepository,
                           TenantContext tenantContext, OrganizationService organizationService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
    }

    @Transactional
    public Product create(ProductCreateRequest request) {
        Organization organization = organizationService.getCurrentOrganization();
        String normalizedSku = normalizeSku(request.sku());

        if (productRepository.existsByOrganizationIdAndSkuIgnoreCase(organization.getId(), normalizedSku)) {
            throw new DuplicateSkuException(normalizedSku);
        }

        ProductCategory category = resolveCategory(request.categoryId(), organization.getId());
        BigDecimal taxPercentage = request.taxPercentage() != null ? request.taxPercentage() : BigDecimal.ZERO;

        Product product = new Product(
                organization,
                category,
                normalizedSku,
                request.name().trim(),
                trimToNull(request.description()),
                request.unit().trim(),
                request.price(),
                taxPercentage
        );

        try {
            // saveAndFlush: surfaces the organization-scoped SKU unique-index
            // violation here as a clean 409, matching the pre-check-then-flush
            // pattern already used by UserService/CustomerService for the
            // equivalent race condition.
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateSkuException(normalizedSku);
        }
    }

    @Transactional(readOnly = true)
    public Product getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        UUID organizationId = tenantContext.currentOrganizationId();
        return productRepository.search(
                organizationId,
                criteria.status(),
                criteria.categoryId(),
                normalizeUnit(criteria.unit()),
                normalizeSearch(criteria.search()),
                pageable
        );
    }

    @Transactional
    public Product update(UUID id, ProductUpdateRequest request) {
        Product product = findOrThrow(id);
        UUID organizationId = product.getOrganization().getId();

        if (request.sku() != null) {
            String normalizedSku = normalizeSku(request.sku());
            if (!normalizedSku.equalsIgnoreCase(product.getSku())
                    && productRepository.existsByOrganizationIdAndSkuIgnoreCase(organizationId, normalizedSku)) {
                throw new DuplicateSkuException(normalizedSku);
            }
            product.setSku(normalizedSku);
        }
        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw new InvalidProductDataException("name cannot be blank");
            }
            product.setName(request.name().trim());
        }
        if (request.description() != null) {
            product.setDescription(trimToNull(request.description()));
        }
        if (request.unit() != null) {
            if (!StringUtils.hasText(request.unit())) {
                throw new InvalidProductDataException("unit cannot be blank");
            }
            product.setUnit(request.unit().trim());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.taxPercentage() != null) {
            product.setTaxPercentage(request.taxPercentage());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.clearCategory()) {
            product.setCategory(null);
        } else if (request.categoryId() != null) {
            product.setCategory(resolveCategory(request.categoryId(), organizationId));
        }

        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateSkuException(product.getSku());
        }
    }

    /**
     * CLAUDE.md §13 names no "delete/archive" feature for products, only
     * "CRUD" — this satisfies CRUD's delete without a second, redundant
     * lifecycle field: transitioning to {@link ProductStatus#INACTIVE} is
     * idempotent (setting an already-inactive product to inactive again is a
     * harmless no-op) and fully reversible via {@link #update}.
     */
    @Transactional
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductCategory resolveCategory(UUID categoryId, UUID organizationId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndOrganizationId(categoryId, organizationId)
                .orElseThrow(() -> new InvalidProductCategoryException(categoryId));
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private static String normalizeUnit(String unit) {
        // Pre-lowercased in Java, then compared as `LOWER(p.unit) = :unit` (the
        // column is wrapped in LOWER(), never the bind parameter itself) — a
        // bind parameter passed as null directly into a PostgreSQL LOWER(...)
        // call fails to type-infer ("function lower(bytea) does not exist"),
        // since there's no other context establishing its type.
        String trimmed = trimToNull(unit);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static String normalizeSearch(String search) {
        String trimmed = trimToNull(search);
        return trimmed == null ? null : "%" + trimmed.toLowerCase() + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
