package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bundles the optional list/search filters (project instructions §15) into
 * one object rather than an 8-parameter method signature — an internal
 * service-layer artifact, not a REST DTO; the controller builds one from
 * individual query parameters.
 */
public record LeadSearchCriteria(
        LeadStatus status,
        LeadSource source,
        LeadPriority priority,
        UUID assignedToUserId,
        boolean unassignedOnly,
        LocalDate followUpOnOrBefore,
        boolean archivedOnly,
        String search
) {
}
