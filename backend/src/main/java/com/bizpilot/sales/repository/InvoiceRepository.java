package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.Invoice;
import com.bizpilot.sales.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    /**
     * {@code statuses} (Phase 17, project instructions §17) is an
     * additional, independent status-<em>set</em> filter alongside the
     * existing single-value {@code status} — {@code ai.tools.InvoiceTools}
     * is the only caller that ever populates it (with
     * {@code ISSUED}/{@code PARTIALLY_PAID}/{@code OVERDUE}), always passing
     * {@code null} for {@code status} in that call; every existing caller
     * (the REST search endpoint) continues to pass {@code null} for {@code
     * statuses}, so this clause never applies to it. {@code :statuses IS
     * NULL OR ...} is the same "optional filter" idiom already used for
     * every other parameter here — verified (Phase 17 implementation) to
     * bind a null {@code List} parameter correctly against a real
     * PostgreSQL/Testcontainers database, not assumed.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.organization.id = :organizationId
              AND (:status IS NULL OR i.status = :status)
              AND (:statuses IS NULL OR i.status IN :statuses)
              AND (:customerId IS NULL OR i.customer.id = :customerId)
              AND (:dueDateBefore IS NULL
                   OR (i.dueDate IS NOT NULL AND i.dueDate <= :dueDateBefore))
            """)
    Page<Invoice> search(@Param("organizationId") UUID organizationId,
                          @Param("status") InvoiceStatus status,
                          @Param("statuses") List<InvoiceStatus> statuses,
                          @Param("customerId") UUID customerId,
                          @Param("dueDateBefore") LocalDate dueDateBefore,
                          Pageable pageable);

    /**
     * Phase 19 (CLAUDE.md §22, locked definition §20): "revenue" is
     * collected/paid revenue — the sum of {@code total} for {@code PAID}
     * invoices created on or after {@code since} (the caller passes
     * {@code now - 30 days}). Returns {@code null} when no row matches
     * (standard JPQL {@code SUM} behavior over zero rows); {@code
     * AnalyticsService} is responsible for the null-to-{@code ZERO}
     * fallback, not this query, so this method's contract exactly matches
     * plain JPQL semantics rather than hiding a COALESCE assumption behind
     * it.
     */
    @Query("""
            SELECT SUM(i.total) FROM Invoice i
            WHERE i.organization.id = :organizationId
              AND i.status = com.bizpilot.sales.entity.InvoiceStatus.PAID
              AND i.createdAt >= :since
            """)
    BigDecimal sumPaidTotalCreatedOnOrAfter(@Param("organizationId") UUID organizationId,
                                             @Param("since") Instant since);

    /**
     * Phase 19 (locked definition §21): "outstanding invoices" — count and
     * total for {@code ISSUED}/{@code PARTIALLY_PAID}/{@code OVERDUE}
     * invoices. Reuses {@code Invoice.total} (the only monetary amount
     * this entity has — there is no separate remaining-balance field, see
     * {@code AnalyticsService}'s Javadoc for the documented limitation this
     * implies for {@code PARTIALLY_PAID} invoices specifically).
     */
    @Query("""
            SELECT COUNT(i) FROM Invoice i
            WHERE i.organization.id = :organizationId
              AND i.status IN :statuses
            """)
    long countByStatuses(@Param("organizationId") UUID organizationId,
                          @Param("statuses") List<InvoiceStatus> statuses);

    @Query("""
            SELECT SUM(i.total) FROM Invoice i
            WHERE i.organization.id = :organizationId
              AND i.status IN :statuses
            """)
    BigDecimal sumTotalByStatuses(@Param("organizationId") UUID organizationId,
                                   @Param("statuses") List<InvoiceStatus> statuses);
}
