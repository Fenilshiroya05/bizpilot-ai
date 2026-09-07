package com.bizpilot.documents.entity;

/**
 * Fixed by CLAUDE.md §16 — exactly these 4 values, no others.
 *
 * <p>Phase 13 only ever persists {@link #UPLOADED} — there is no processing
 * pipeline yet, so {@link #PROCESSING}/{@link #COMPLETED} are never reached
 * by any code path in this phase, and a failed upload never produces a
 * persisted row at all (see {@code DocumentService.upload}'s Javadoc) rather
 * than persisting one in {@link #FAILED}. Both values exist now purely so
 * the column's {@code CHECK} constraint and this enum are already correct
 * and won't need a future migration once the Phase 15 RAG pipeline starts
 * setting them.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED
}
