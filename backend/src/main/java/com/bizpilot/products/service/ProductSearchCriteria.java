package com.bizpilot.products.service;

import com.bizpilot.products.entity.ProductStatus;

import java.util.UUID;

/**
 * Bundles the optional list/search filters (project instructions §17) into
 * one object rather than a long method parameter list — an internal
 * service-layer artifact, not a REST DTO; the controller builds one from
 * individual query parameters. Mirrors {@code sales.service.LeadSearchCriteria}.
 */
public record ProductSearchCriteria(
        ProductStatus status,
        UUID categoryId,
        String unit,
        String search
) {
}
