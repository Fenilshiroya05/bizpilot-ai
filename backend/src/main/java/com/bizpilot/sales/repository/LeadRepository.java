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

import java.time.LocalDate;
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
}
