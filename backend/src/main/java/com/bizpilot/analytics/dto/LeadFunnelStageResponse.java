package com.bizpilot.analytics.dto;

import com.bizpilot.sales.entity.LeadStatus;

/**
 * One stage of the Phase 26 lead funnel chart (CLAUDE.md §22) — the actual
 * current {@link LeadStatus} distribution across every lead in the caller's
 * organization. {@link Lead} has no separate funnel-stage concept, so the
 * existing 7-value status enum is the funnel model itself, not a fabricated
 * one.
 */
public record LeadFunnelStageResponse(LeadStatus status, long count) {
}
