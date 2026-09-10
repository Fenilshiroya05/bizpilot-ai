package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.Quotation;
import com.bizpilot.sales.entity.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7 / project instructions
     * §17): never {@code findById} alone for a tenant-scoped entity. Use
     * this variant when the caller does not need {@code items} (e.g.
     * cancel/archive, which returns no body).
     */
    Optional<Quotation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Same tenant-safe lookup, but eagerly fetches {@code items} in the same
     * query — required whenever the returned {@code Quotation} will be
     * mapped to a response (or rendered to a PDF) *after* the transactional
     * service method returns, since {@code items} is a lazy collection and
     * {@code spring.jpa.open-in-view=false} means the persistence session is
     * already closed by the time a controller/mapper would otherwise touch
     * it. Never used for paginated queries — a {@code JOIN FETCH} on a
     * {@code @OneToMany} combined with {@code Pageable} is a well-known JPA
     * trap (Hibernate must apply pagination in-memory, silently returning
     * wrong page sizes) — see {@link #search} below, which deliberately does
     * NOT fetch items for exactly this reason.
     */
    @Query("""
            SELECT q FROM Quotation q
            LEFT JOIN FETCH q.items
            WHERE q.id = :id AND q.organization.id = :organizationId
            """)
    Optional<Quotation> findByIdAndOrganizationIdWithItems(@Param("id") UUID id,
                                                            @Param("organizationId") UUID organizationId);

    /**
     * No free-text search: unlike Customer/Lead/Product, {@code Quotation}
     * has no natural text field of its own to search (name/SKU/description
     * equivalents don't exist here) — filtering by status/customer/validity
     * is the meaningful query surface for this resource (an implementation
     * decision, documented in docs/database.md). All filters are optional (a
     * {@code null} parameter matches every value); pagination happens
     * entirely at the database level.
     */
    @Query("""
            SELECT q FROM Quotation q
            WHERE q.organization.id = :organizationId
              AND (:status IS NULL OR q.status = :status)
              AND (:customerId IS NULL OR q.customer.id = :customerId)
              AND (:validUntilBefore IS NULL
                   OR (q.validUntil IS NOT NULL AND q.validUntil <= :validUntilBefore))
            """)
    Page<Quotation> search(@Param("organizationId") UUID organizationId,
                            @Param("status") QuotationStatus status,
                            @Param("customerId") UUID customerId,
                            @Param("validUntilBefore") LocalDate validUntilBefore,
                            Pageable pageable);

    /**
     * Phase 26 (CLAUDE.md §22 "sales pipeline" chart): grouped quotation
     * count and total {@code grandTotal} per {@code status} — the actual
     * quotation lifecycle status is the pipeline "stage" model in this
     * domain; there is no separate sales-stage concept to represent instead.
     * Only statuses with at least one matching quotation appear in the
     * result.
     */
    @Query("""
            SELECT q.status AS status, COUNT(q) AS count, SUM(q.grandTotal) AS amount
            FROM Quotation q
            WHERE q.organization.id = :organizationId
            GROUP BY q.status
            """)
    List<QuotationPipelineRow> countAndSumGroupedByStatus(@Param("organizationId") UUID organizationId);
}
