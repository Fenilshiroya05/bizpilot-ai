package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        String name,
        String company,
        String email,
        String phone,
        LeadStatus status,
        LeadSource source,
        LeadPriority priority,
        LocalDate followUpDate,
        UUID assignedToUserId,
        Instant archivedAt,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
