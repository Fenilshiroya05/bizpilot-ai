package com.bizpilot.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal for a request, built directly from a validated
 * JWT's claims by {@link JwtAuthenticationFilter} — no database lookup per
 * request. Login itself is verified manually in {@code AuthService} (not via
 * {@code AuthenticationManager}), so the exact order of checks (password
 * before account status) is under our control; see docs/security.md.
 *
 * <p>{@code organizationId} is the sole basis for tenant resolution
 * ({@code organization.TenantContext}) — it comes only from the signed JWT,
 * never from client-supplied request data (CLAUDE.md §7).
 *
 * <p>{@code authorities} (Phase 6) holds both role authorities
 * ({@code ROLE_<name>}) and permission-name authorities (e.g.
 * {@code CUSTOMER_READ}), pre-resolved from the {@code roles}/
 * {@code role_permissions} model at token-issuance time — see
 * {@code security.jwt.JwtService}. Spring Security's own {@code ROLE_}
 * prefix convention is what lets {@code hasRole(...)} and
 * {@code hasAuthority(...)} both work off this single flat set.
 */
public record UserPrincipal(UUID userId, UUID organizationId, Set<String> authorities) {
}
