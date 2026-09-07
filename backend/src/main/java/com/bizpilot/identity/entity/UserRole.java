package com.bizpilot.identity.entity;

/**
 * The fixed set of role names defined by CLAUDE.md §9 — do not add roles
 * beyond these. The authoritative storage for role assignment (and each
 * role's permissions) is the {@code roles}/{@code permissions}/
 * {@code user_roles}/{@code role_permissions} model ({@link Role},
 * {@link Permission}, seeded by Flyway V4). This enum exists purely for
 * type-safe references to those seeded row names in Java code (e.g. looking
 * up the default role at registration) — it is not itself persisted.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    MANAGER,
    SALES,
    EMPLOYEE
}
