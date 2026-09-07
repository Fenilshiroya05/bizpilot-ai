package com.bizpilot.products.repository;

import com.bizpilot.products.entity.Product;
import com.bizpilot.products.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7 / project instructions
     * §12): never {@code findById} alone for a tenant-scoped entity.
     */
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndSkuIgnoreCase(UUID organizationId, String sku);

    /**
     * Unlike Customer/Lead, there is no "excluded by default" lifecycle
     * state here — {@link ProductStatus#INACTIVE} means "intentionally
     * unavailable," not "archived," so default listing includes both
     * statuses unless the caller explicitly filters by one (see
     * {@code ProductService}/docs/database.md for the reasoning). All
     * filters are optional (a {@code null} parameter matches every value);
     * pagination and filtering happen entirely at the database level.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.organization.id = :organizationId
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:unit IS NULL OR LOWER(p.unit) = :unit)
              AND (:search IS NULL
                   OR LOWER(p.sku) LIKE :search
                   OR LOWER(p.name) LIKE :search
                   OR LOWER(p.description) LIKE :search)
            """)
    Page<Product> search(@Param("organizationId") UUID organizationId,
                          @Param("status") ProductStatus status,
                          @Param("categoryId") UUID categoryId,
                          @Param("unit") String unit,
                          @Param("search") String search,
                          Pageable pageable);
}
