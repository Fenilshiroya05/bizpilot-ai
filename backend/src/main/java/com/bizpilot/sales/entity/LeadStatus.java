package com.bizpilot.sales.entity;

/**
 * Fixed by CLAUDE.md §11 — exactly these 7 values, no others. Represents the
 * lead's business-pipeline outcome, not its record lifecycle: a lead can be
 * {@code WON} or {@code LOST} and still be a non-archived record (e.g. kept
 * visible for reporting) — see {@link Lead#getArchivedAt()} for the separate
 * archive/record-lifecycle concept.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    PROPOSAL,
    NEGOTIATION,
    WON,
    LOST
}
