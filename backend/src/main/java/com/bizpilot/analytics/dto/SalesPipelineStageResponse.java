package com.bizpilot.analytics.dto;

import com.bizpilot.sales.entity.QuotationStatus;

import java.math.BigDecimal;

/**
 * One stage of the Phase 26 sales pipeline chart (CLAUDE.md §22) — the
 * actual {@link QuotationStatus} lifecycle is the pipeline "stage" model in
 * this domain; there is no separate sales-stage concept to represent instead
 * (project instructions: do not invent one). {@code amount} is the sum of
 * {@code grandTotal} for quotations in this status.
 */
public record SalesPipelineStageResponse(QuotationStatus status, long count, BigDecimal amount) {
}
