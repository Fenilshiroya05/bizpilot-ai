package com.bizpilot.tasks.entity;

/**
 * Fixed by CLAUDE.md §23 — exactly these 4 values, no others.
 *
 * <p>Unlike {@code QuotationStatus}/{@code InvoiceStatus}, reaching
 * {@code CANCELLED} is the only status transition restricted to a dedicated
 * action ({@code DELETE /api/v1/tasks/{id}}) — every other transition,
 * including reopening from {@code COMPLETED}/{@code CANCELLED} back to
 * {@code TODO}/{@code IN_PROGRESS}, is freely allowed via the general update
 * endpoint (an explicit, approved Phase 12 decision — Tasks are not
 * immutable at any status, unlike Invoice).
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
