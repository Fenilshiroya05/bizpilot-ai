package com.bizpilot.sales.repository;

import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7 / project instructions
     * §16): never {@code findById} alone for a tenant-scoped entity.
     */
    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Default listing behavior: excludes archived leads unless the caller
     * explicitly asks to see them ({@code archivedOnly}). All filters are
     * optional (a {@code null} parameter matches every value); pagination
     * and filtering happen entirely at the database level.
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE l.organization.id = :organizationId
              AND ((:archivedOnly = TRUE AND l.archivedAt IS NOT NULL)
                   OR (:archivedOnly = FALSE AND l.archivedAt IS NULL))
              AND (:status IS NULL OR l.status = :status)
              AND (:source IS NULL OR l.source = :source)
              AND (:priority IS NULL OR l.priority = :priority)
              AND (:assignedToUserId IS NULL OR l.assignedToUserId = :assignedToUserId)
              AND (:unassignedOnly = FALSE OR l.assignedToUserId IS NULL)
              AND (:followUpOnOrBefore IS NULL
                   OR (l.followUpDate IS NOT NULL AND l.followUpDate <= :followUpOnOrBefore))
              AND (:search IS NULL
                   OR LOWER(l.name) LIKE :search
                   OR LOWER(l.company) LIKE :search
                   OR LOWER(l.email) LIKE :search)
            """)
    Page<Lead> search(@Param("organizationId") UUID organizationId,
                       @Param("archivedOnly") boolean archivedOnly,
                       @Param("status") LeadStatus status,
                       @Param("source") LeadSource source,
                       @Param("priority") LeadPriority priority,
                       @Param("assignedToUserId") UUID assignedToUserId,
                       @Param("unassignedOnly") boolean unassignedOnly,
                       @Param("followUpOnOrBefore") LocalDate followUpOnOrBefore,
                       @Param("search") String search,
                       Pageable pageable);

    /**
     * Phase 19 (CLAUDE.md §22, locked definition §17): "new leads" — a
     * count of non-archived leads created on or after {@code since} (the
     * caller passes {@code now - 30 days}; this method itself accepts no
     * client-controlled period, per the locked "no ?since=/?days=" rule).
     * Excludes archived leads for the same reason {@link #search}'s
     * default listing already does — an archived lead is a hidden record,
     * consistent across every lead-facing query in this codebase.
     */
    @Query("""
            SELECT COUNT(l) FROM Lead l
            WHERE l.organization.id = :organizationId
              AND l.archivedAt IS NULL
              AND l.createdAt >= :since
            """)
    long countCreatedOnOrAfterExcludingArchived(@Param("organizationId") UUID organizationId,
                                                 @Param("since") Instant since);

    /**
     * Phase 19 (locked definitions §18/§19): a plain current-status count,
     * with no archived filter — the locked scope defines "qualified leads"
     * and the WON/LOST conversion-rate inputs purely by {@code status},
     * deliberately not by archive state (unlike {@link
     * #countCreatedOnOrAfterExcludingArchived}/{@link
     * #countPendingFollowUpsExcludingArchived}, whose locked definitions do
     * name archived exclusion explicitly). Reused for three distinct Phase
     * 19 metrics (qualified/won/lost) rather than one method per status.
     */
    @Query("""
            SELECT COUNT(l) FROM Lead l
            WHERE l.organization.id = :organizationId
              AND l.status = :status
            """)
    long countByStatus(@Param("organizationId") UUID organizationId, @Param("status") LeadStatus status);

    /**
     * Phase 19 (locked definition §22): "pending follow-ups" — a
     * non-archived lead with a {@code followUpDate} due today or earlier,
     * whose status is not {@code WON}/{@code LOST}. Deliberately excludes
     * {@code Task} due dates entirely (locked scope: this is a Lead-only
     * metric).
     */
    @Query("""
            SELECT COUNT(l) FROM Lead l
            WHERE l.organization.id = :organizationId
              AND l.archivedAt IS NULL
              AND l.followUpDate IS NOT NULL
              AND l.followUpDate <= :onOrBefore
              AND l.status NOT IN (com.bizpilot.sales.entity.LeadStatus.WON,
                                    com.bizpilot.sales.entity.LeadStatus.LOST)
            """)
    long countPendingFollowUpsExcludingArchived(@Param("organizationId") UUID organizationId,
                                                 @Param("onOrBefore") LocalDate onOrBefore);

    /**
     * Phase 26 (CLAUDE.md §22 "lead sources" chart): grouped lead count per
     * {@code source} — same "no archived filter" convention as {@link
     * #countByStatus} (a source distribution reflects every current lead,
     * archived or not). Only sources with at least one matching lead appear
     * in the result; no zero-count rows are synthesized.
     */
    @Query("""
            SELECT l.source AS source, COUNT(l) AS count
            FROM Lead l
            WHERE l.organization.id = :organizationId
            GROUP BY l.source
            """)
    List<LeadSourceCount> countGroupedBySource(@Param("organizationId") UUID organizationId);
}
