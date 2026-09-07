package com.bizpilot.sales.dto;

import java.util.UUID;

/**
 * Dedicated request for the {@code /assign} action — unlike
 * {@code LeadUpdateRequest}, {@code null} here has an explicit, unambiguous
 * meaning: unassign the lead. A non-null value assigns to that user id
 * (validated to belong to the caller's organization in {@code LeadService}).
 */
public record LeadAssignRequest(
        UUID assigneeUserId
) {
}
