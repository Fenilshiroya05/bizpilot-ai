package com.bizpilot.identity.entity;

/**
 * Account status per docs/security.md §1 ("active/disabled/locked").
 * {@code LOCKED} is a valid, checked state, but nothing currently transitions
 * a user into it automatically — that arrives with rate-limiting/brute-force
 * protection (CLAUDE.md §26), not part of Phase 4.
 */
public enum UserStatus {
    ACTIVE,
    DISABLED,
    LOCKED
}
