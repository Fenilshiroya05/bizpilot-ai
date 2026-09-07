package com.bizpilot.security;

import com.bizpilot.identity.entity.UserRole;

import java.util.UUID;

/**
 * The authenticated principal for a request, built directly from a validated
 * JWT's claims by {@link JwtAuthenticationFilter} — no database lookup per
 * request. Login itself is verified manually in {@code AuthService} (not via
 * {@code AuthenticationManager}), so the exact order of checks (password
 * before account status) is under our control; see docs/security.md.
 */
public record UserPrincipal(UUID userId, UserRole role) {
}
