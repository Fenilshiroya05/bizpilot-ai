package com.bizpilot.sales.entity;

/**
 * CLAUDE.md §11 requires a priority field but defines no values — an
 * implementation decision (see docs/database.md). Deliberately a distinct,
 * smaller set from Task priority (LOW/MEDIUM/HIGH/URGENT, CLAUDE.md §23)
 * rather than reused wholesale: a lead's priority reflects sales value /
 * likelihood-to-close for triage purposes, not operational urgency, and
 * doesn't need an "URGENT" escalation tier the way an operational task might.
 */
public enum LeadPriority {
    LOW,
    MEDIUM,
    HIGH
}
