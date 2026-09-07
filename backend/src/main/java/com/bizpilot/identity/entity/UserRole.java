package com.bizpilot.identity.entity;

/**
 * RBAC roles defined by CLAUDE.md §9. Full granular permissions (per-role
 * permission catalog, {@code roles}/{@code permissions}/{@code user_roles}
 * tables) are Phase 6 scope — Phase 4 only assigns a single role per user.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    MANAGER,
    SALES,
    EMPLOYEE
}
