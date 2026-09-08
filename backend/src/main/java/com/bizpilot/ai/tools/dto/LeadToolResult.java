package com.bizpilot.ai.tools.dto;

import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A tool-facing view of a {@code Lead} (Phase 17) — mirrors {@link
 * CustomerToolResult}'s exact rationale: smaller than {@code
 * sales.dto.LeadResponse}, omitting {@code organizationId}/{@code
 * archivedAt}/{@code createdAt}/{@code updatedAt}.
 */
public record LeadToolResult(
        UUID id,
        String name,
        String company,
        String email,
        String phone,
        LeadStatus status,
        LeadSource source,
        LeadPriority priority,
        LocalDate followUpDate,
        UUID assignedToUserId
) {
}
