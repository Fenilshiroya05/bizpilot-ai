package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.QuotationStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bundles the optional list/search filters (project instructions §15) into
 * one object — an internal service-layer artifact, not a REST DTO; mirrors
 * {@code sales.service.LeadSearchCriteria}/{@code products.service.ProductSearchCriteria}.
 */
public record QuotationSearchCriteria(
        QuotationStatus status,
        UUID customerId,
        LocalDate validUntilBefore
) {
}
