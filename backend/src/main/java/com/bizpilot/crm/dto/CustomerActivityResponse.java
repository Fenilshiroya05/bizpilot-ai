package com.bizpilot.crm.dto;

import com.bizpilot.crm.entity.CustomerActivityType;

import java.time.Instant;
import java.util.UUID;

public record CustomerActivityResponse(
        UUID id,
        CustomerActivityType type,
        String content,
        UUID createdByUserId,
        Instant createdAt
) {
}
