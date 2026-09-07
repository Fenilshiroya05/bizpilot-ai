package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.InvoiceStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bundles the optional list/search filters into one object — an internal
 * service-layer artifact, not a REST DTO; mirrors
 * {@code sales.service.QuotationSearchCriteria}.
 */
public record InvoiceSearchCriteria(
        InvoiceStatus status,
        UUID customerId,
        LocalDate dueDateBefore
) {
}
