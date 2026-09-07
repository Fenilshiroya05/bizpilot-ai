package com.bizpilot.products.entity;

/**
 * CLAUDE.md §13 explicitly requires "Active/inactive status" — unlike
 * Customer/Lead status, this is a simple, CLAUDE.md-given 2-value concept,
 * not an implementation decision. Deliberately just these two values: no
 * {@code ARCHIVED}/{@code DELETED}/{@code DRAFT}/{@code DISCONTINUED} — see
 * {@code ProductService}'s Javadoc for why delete is modeled as a transition
 * to {@code INACTIVE} rather than a separate archive mechanism.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
