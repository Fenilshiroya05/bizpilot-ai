package com.bizpilot.ai.tools.dto;

import com.bizpilot.crm.entity.CustomerStatus;

import java.util.UUID;

/**
 * A tool-facing view of a {@code Customer} (Phase 17, project instructions
 * §13) — deliberately smaller than {@code crm.dto.CustomerResponse}:
 * {@code organizationId}/{@code createdAt}/{@code updatedAt} are omitted as
 * pure internal plumbing with no conversational value and no reason to ever
 * reach the model's context.
 */
public record CustomerToolResult(
        UUID id,
        String name,
        String company,
        String email,
        String phone,
        String address,
        String gstin,
        CustomerStatus status,
        String notes
) {
}
