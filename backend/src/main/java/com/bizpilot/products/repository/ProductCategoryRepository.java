package com.bizpilot.products.repository;

import com.bizpilot.products.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7 / project instructions
     * §12): never {@code findById} alone for a tenant-scoped entity. Also
     * used to validate a category referenced by a {@code Product} actually
     * belongs to the current organization.
     */
    Optional<ProductCategory> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<ProductCategory> findByOrganizationId(UUID organizationId, Pageable pageable);
}
