package com.bizpilot.documents.service;

import com.bizpilot.documents.entity.DocumentStatus;

import java.util.UUID;

/**
 * Bundles the optional list/search filters into one object — an internal
 * service-layer artifact, not a REST DTO; mirrors
 * {@code sales.service.LeadSearchCriteria}/{@code tasks.service.TaskSearchCriteria}.
 */
public record DocumentSearchCriteria(
        DocumentStatus status,
        String contentType,
        UUID uploadedByUserId,
        String search
) {
}
