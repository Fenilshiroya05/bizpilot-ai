package com.bizpilot.crm.dto;

import com.bizpilot.crm.entity.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String company,
        String email,
        String phone,
        String address,
        String gstin,
        CustomerStatus status,
        String notes,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
