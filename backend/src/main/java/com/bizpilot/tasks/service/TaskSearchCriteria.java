package com.bizpilot.tasks.service;

import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bundles the optional list/search filters into one object — an internal
 * service-layer artifact, not a REST DTO; mirrors
 * {@code sales.service.LeadSearchCriteria}.
 */
public record TaskSearchCriteria(
        TaskStatus status,
        TaskPriority priority,
        UUID assignedToUserId,
        boolean unassignedOnly,
        UUID customerId,
        UUID leadId,
        LocalDate dueDateOnOrBefore,
        String search
) {
}
