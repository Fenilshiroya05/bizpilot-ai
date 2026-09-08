package com.bizpilot.analytics.dto;

import java.math.BigDecimal;

/**
 * The Phase 19 business summary (CLAUDE.md §22's dashboard-card numbers) —
 * deterministic, server-computed, tenant-scoped. Every numeric field is
 * always present (never {@code null}): a tenant with no matching data
 * returns {@code 0}/{@code 0.0000}, never a missing/null field.
 *
 * <p>No {@code organizationId}/tenant identifier of any kind is included —
 * the organization is implicit (the authenticated caller's own tenant) and
 * never needs to round-trip through the response.
 */
public record AnalyticsSummaryResponse(
        long totalCustomers,
        long newLeads,
        long qualifiedLeads,
        BigDecimal conversionRate,
        BigDecimal revenue,
        long outstandingInvoicesCount,
        BigDecimal outstandingInvoicesTotal,
        long pendingFollowUps
) {
}
