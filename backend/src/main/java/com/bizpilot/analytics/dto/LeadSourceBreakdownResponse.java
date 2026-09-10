package com.bizpilot.analytics.dto;

import com.bizpilot.sales.entity.LeadSource;

/**
 * One entry of the Phase 26 lead sources chart (CLAUDE.md §22). Only sources
 * with at least one matching lead appear — {@code Lead.source} is a
 * non-nullable column (see the entity), so there is no null/blank source to
 * represent; a source with zero leads simply has no row here.
 */
public record LeadSourceBreakdownResponse(LeadSource source, long count) {
}
