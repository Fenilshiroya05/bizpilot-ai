package com.bizpilot.products.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductCategoryResponse(
        UUID id,
        String name,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
