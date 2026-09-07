package com.bizpilot.sales.entity;

/**
 * Discriminates the single {@code lead_activities} log into the CLAUDE.md
 * §11 features it backs ("Lead activities", "Notes"), without a project-wide
 * audit/event-sourcing framework — same pattern as
 * {@code crm.entity.CustomerActivityType} (Phase 7).
 *
 * <ul>
 *   <li>{@link #NOTE} — a user-authored free-text note ("Notes").</li>
 *   <li>{@link #CREATED} / {@link #STATUS_CHANGED} / {@link #ASSIGNED} /
 *       {@link #ARCHIVED} — system-recorded lifecycle events ("Lead
 *       activities").</li>
 * </ul>
 *
 * "Lead history" is the full chronological feed across every type — see
 * {@code LeadService#getHistory}.
 */
public enum LeadActivityType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNED,
    ARCHIVED,
    NOTE
}
