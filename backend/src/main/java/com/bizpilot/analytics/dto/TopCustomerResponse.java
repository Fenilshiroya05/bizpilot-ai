package com.bizpilot.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the Phase 26 top-customers widget (CLAUDE.md §22) — a
 * customer's total PAID-invoice revenue, all-time (not the 30-day window
 * used elsewhere in this service), ranked highest first and bounded to a
 * fixed top-N by {@code AnalyticsService}.
 */
public record TopCustomerResponse(UUID customerId, String customerName, BigDecimal revenue) {
}
