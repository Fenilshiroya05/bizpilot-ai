package com.bizpilot.sales.entity;

/**
 * Fixed by CLAUDE.md §15 — exactly these 6 values, no others.
 * {@code PARTIALLY_PAID}/{@code PAID}/{@code OVERDUE} represent payment
 * progress within this single status field — CLAUDE.md gives one concrete
 * status enum, not a second, separate payment-status model (Phase 11
 * implementation decision, confirmed).
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}
