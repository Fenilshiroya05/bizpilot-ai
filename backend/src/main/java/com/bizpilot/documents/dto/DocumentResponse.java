package com.bizpilot.documents.dto;

import com.bizpilot.documents.entity.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Used for both the single-resource endpoint and the paginated list — no
 * lazy child collection exists on {@code Document} (mirrors
 * {@code tasks.dto.TaskResponse}'s identical reasoning), so a separate
 * summary/detail split isn't needed.
 *
 * <p><b>Deliberately excludes</b> {@code storageKey}, {@code organizationId},
 * and any filesystem/storage path — never exposed to a client (project
 * instructions §26).
 */
public record DocumentResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long fileSize,
        DocumentStatus status,
        UUID uploadedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
