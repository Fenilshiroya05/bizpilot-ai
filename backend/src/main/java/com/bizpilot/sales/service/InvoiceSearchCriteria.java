package com.bizpilot.sales.service;

import com.bizpilot.sales.entity.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bundles the optional list/search filters into one object — an internal
 * service-layer artifact, not a REST DTO; mirrors
 * {@code sales.service.QuotationSearchCriteria}.
 *
 * <p>{@code statuses} (Phase 17, project instructions §17): an optional
 * status-<em>set</em> filter, additive alongside the existing single-value
 * {@code status} — added so {@code ai.tools.InvoiceTools.getOutstandingInvoices}
 * can filter by {@code ISSUED}/{@code PARTIALLY_PAID}/{@code OVERDUE} in one
 * query, without a parallel data-access path or a breaking change to the
 * existing single-status REST search ({@code InvoiceController.search},
 * unaffected, always passes {@code null} here). The two filters are
 * independent: a caller supplying both would match rows satisfying either
 * (see {@code InvoiceRepository.search}'s JPQL) — in practice, exactly one
 * of the two is ever populated by any current caller.
 */
public record InvoiceSearchCriteria(
        InvoiceStatus status,
        UUID customerId,
        LocalDate dueDateBefore,
        List<InvoiceStatus> statuses
) {
}
