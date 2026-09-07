package com.bizpilot.sales.dto;

import com.bizpilot.sales.entity.LeadActivityType;

import java.time.Instant;
import java.util.UUID;

public record LeadActivityResponse(
        UUID id,
        LeadActivityType type,
        String content,
        UUID createdByUserId,
        Instant createdAt
) {
}
