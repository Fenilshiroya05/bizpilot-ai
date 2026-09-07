package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.Invoice;
import com.bizpilot.sales.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7): never {@code findById}
     * alone for a tenant-scoped entity. Use this variant when the caller does
     * not need {@code items} (e.g. cancel, which returns no body).
     */
    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Same tenant-safe lookup, but eagerly fetches {@code items} in the same
     * query — required whenever the returned {@code Invoice} will be mapped
     * to a response (or rendered to a PDF) after the transactional service
     * method returns. Never used for paginated queries — see {@link #search}.
     */
    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN FETCH i.items
            WHERE i.id = :id AND i.organization.id = :organizationId
            """)
    Optional<Invoice> findByIdAndOrganizationIdWithItems(@Param("id") UUID id,
                                                          @Param("organizationId") UUID organizationId);

    /**
     * No free-text search: like {@code Quotation}, {@code Invoice} has no
     * natural text field of its own. Deliberately does NOT fetch
     * {@code items} — a {@code JOIN FETCH} on a {@code @OneToMany} combined
     * with {@code Pageable} silently breaks pagination (Hibernate would
     * apply it in-memory) — see {@code QuotationRepository}'s identical
     * precedent. All filters are optional (a {@code null} parameter matches
     * every value); pagination happens entirely at the database level.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.organization.id = :organizationId
              AND (:status IS NULL OR i.status = :status)
              AND (:customerId IS NULL OR i.customer.id = :customerId)
              AND (:dueDateBefore IS NULL
                   OR (i.dueDate IS NOT NULL AND i.dueDate <= :dueDateBefore))
            """)
    Page<Invoice> search(@Param("organizationId") UUID organizationId,
                          @Param("status") InvoiceStatus status,
                          @Param("customerId") UUID customerId,
                          @Param("dueDateBefore") LocalDate dueDateBefore,
                          Pageable pageable);
}
