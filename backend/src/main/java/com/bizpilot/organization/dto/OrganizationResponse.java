package com.bizpilot.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        Instant createdAt
) {
}
