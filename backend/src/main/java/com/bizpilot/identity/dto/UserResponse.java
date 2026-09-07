package com.bizpilot.identity.dto;

import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing user representation. Never includes {@code passwordHash} or
 * any other internal security detail.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        Instant createdAt
) {
}
