package com.bizpilot.tasks.dto;

import java.util.UUID;

/**
 * Dedicated request for the {@code /assign} action — unlike
 * {@code TaskUpdateRequest}, {@code null} here has an explicit, unambiguous
 * meaning: unassign the task. A non-null value assigns to that user id
 * (validated to belong to the caller's organization and be
 * {@code ACTIVE} in {@code TaskService}). Mirrors {@code LeadAssignRequest}
 * exactly.
 */
public record TaskAssignRequest(
        UUID assigneeUserId
) {
}
