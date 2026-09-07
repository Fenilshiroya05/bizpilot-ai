package com.bizpilot.crm.repository;

import com.bizpilot.crm.entity.Customer;
import com.bizpilot.crm.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7 / project instructions
     * §12): never {@code findById} alone for a tenant-scoped entity.
     */
    Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    /**
     * Explicit status filter (e.g. {@code status=ARCHIVED} to view archived
     * customers on purpose). Always organization-scoped; pagination and
     * filtering happen at the database level (no in-memory paging).
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.organization.id = :organizationId
              AND c.status = :status
              AND (:search IS NULL
                   OR LOWER(c.name) LIKE :search
                   OR LOWER(c.company) LIKE :search
                   OR LOWER(c.email) LIKE :search)
            """)
    Page<Customer> searchByStatus(@Param("organizationId") UUID organizationId,
                                   @Param("status") CustomerStatus status,
                                   @Param("search") String search,
                                   Pageable pageable);

    /**
     * Default listing behavior: no explicit status filter excludes
     * {@code ARCHIVED} (archived customers must not appear in normal
     * searches/listing per project instructions §7) while still returning
     * every other status.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.organization.id = :organizationId
              AND c.status <> com.bizpilot.crm.entity.CustomerStatus.ARCHIVED
              AND (:search IS NULL
                   OR LOWER(c.name) LIKE :search
                   OR LOWER(c.company) LIKE :search
                   OR LOWER(c.email) LIKE :search)
            """)
    Page<Customer> searchExcludingArchived(@Param("organizationId") UUID organizationId,
                                            @Param("search") String search,
                                            Pageable pageable);
}
