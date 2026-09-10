package com.bizpilot.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point on the Phase 26 revenue trend chart (CLAUDE.md §22) — one entry
 * per calendar day in the fixed {@code RECENT_WINDOW_DAYS} window
 * ({@code AnalyticsService}), every day present even with zero revenue, so
 * the chart's X axis is continuous rather than skipping days with no PAID
 * invoices.
 */
public record RevenueTrendPointResponse(LocalDate period, BigDecimal revenue) {
}
