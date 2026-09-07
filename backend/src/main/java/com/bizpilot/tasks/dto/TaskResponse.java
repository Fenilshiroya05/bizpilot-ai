package com.bizpilot.tasks.dto;

import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Used for both the single-resource endpoint and the paginated list —
 * unlike {@code QuotationResponse}/{@code QuotationSummaryResponse}, a
 * single response shape is sufficient here because {@code Task} has no
 * lazy child collection (no items), so there is no JPA
 * collection-fetch-join-plus-pagination trap to avoid by splitting into a
 * summary/detail pair. Introducing a duplicate {@code TaskSummaryResponse}
 * with the exact same fields would be an unjustified abstraction.
 */
public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assignedToUserId,
        UUID customerId,
        UUID leadId,
        LocalDate dueDate,
        String notes,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
