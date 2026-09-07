package com.bizpilot.identity.dto;

import com.bizpilot.identity.entity.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Public-facing user representation. Never includes {@code passwordHash} or
 * any other internal security detail. {@code roles} lists role names only
 * (e.g. {@code "EMPLOYEE"}) — not the underlying permission catalog, which
 * isn't user-specific data worth repeating on every profile response.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        UserStatus status,
        UUID organizationId,
        Instant createdAt
) {
}
