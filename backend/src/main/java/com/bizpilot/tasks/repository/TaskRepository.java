package com.bizpilot.tasks.repository;

import com.bizpilot.tasks.entity.Task;
import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7): never {@code findById}
     * alone for a tenant-scoped entity.
     */
    Optional<Task> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * All filters are optional (a {@code null}/{@code false} parameter
     * matches every value); pagination and filtering happen entirely at the
     * database level. No archived/excluded-by-default semantics — unlike
     * Lead, a Task has no separate record-lifecycle concept from its status.
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.organization.id = :organizationId
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assignedToUserId IS NULL OR t.assignedToUserId = :assignedToUserId)
              AND (:unassignedOnly = FALSE OR t.assignedToUserId IS NULL)
              AND (:customerId IS NULL OR t.customerId = :customerId)
              AND (:leadId IS NULL OR t.leadId = :leadId)
              AND (:dueDateOnOrBefore IS NULL
                   OR (t.dueDate IS NOT NULL AND t.dueDate <= :dueDateOnOrBefore))
              AND (:search IS NULL OR LOWER(t.title) LIKE :search)
            """)
    Page<Task> search(@Param("organizationId") UUID organizationId,
                       @Param("status") TaskStatus status,
                       @Param("priority") TaskPriority priority,
                       @Param("assignedToUserId") UUID assignedToUserId,
                       @Param("unassignedOnly") boolean unassignedOnly,
                       @Param("customerId") UUID customerId,
                       @Param("leadId") UUID leadId,
                       @Param("dueDateOnOrBefore") LocalDate dueDateOnOrBefore,
                       @Param("search") String search,
                       Pageable pageable);
}
