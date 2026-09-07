package com.bizpilot.crm.entity;

/**
 * CLAUDE.md §10 lists a {@code Status} field but does not define its values —
 * an implementation decision (see docs/database.md). Deliberately small and
 * CRM-generic, distinct from the lead-pipeline statuses in CLAUDE.md §11:
 *
 * <ul>
 *   <li>{@link #ACTIVE} — the default; a normal, currently-engaged customer.</li>
 *   <li>{@link #INACTIVE} — a business-meaningful "not currently engaged"
 *       state (e.g. dormant relationship) that stops short of archiving —
 *       still fully editable and visible in normal listings.</li>
 *   <li>{@link #ARCHIVED} — the soft-delete state (see {@code CustomerService});
 *       reachable only via the dedicated archive operation, never directly
 *       through the general update endpoint.</li>
 * </ul>
 */
public enum CustomerStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
