package com.bizpilot.crm.entity;

/**
 * Discriminates the single {@code customer_activities} log into the three
 * CLAUDE.md §10 features it backs, without three separate tables or a
 * generic audit/event-sourcing framework (see docs/database.md):
 *
 * <ul>
 *   <li>{@link #NOTE} — a user-authored free-text note ("Customer notes").</li>
 *   <li>{@link #CREATED} / {@link #STATUS_CHANGED} / {@link #ARCHIVED} —
 *       system-recorded lifecycle events ("Customer activities").</li>
 * </ul>
 *
 * "Customer history" is the full chronological feed across every type —
 * see {@code CustomerService#getHistory}.
 */
public enum CustomerActivityType {
    CREATED,
    STATUS_CHANGED,
    ARCHIVED,
    NOTE
}
