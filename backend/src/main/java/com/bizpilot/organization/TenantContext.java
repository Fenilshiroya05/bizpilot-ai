package com.bizpilot.organization;

import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place application code asks "what organization does this
 * authenticated request belong to?" — built on {@link CurrentUserProvider}
 * so nothing else needs to touch the security context or JWT directly
 * (CLAUDE.md §7: the organization is always derived from the authenticated
 * identity, never from client-supplied input).
 *
 * <p>Stateless and thread-safe: it reads {@code SecurityContextHolder}
 * (via {@code CurrentUserProvider}) fresh on every call rather than caching
 * anything, so it's safe under concurrent requests and never leaks tenant
 * state between them.
 */
@Component
public class TenantContext {

    private final CurrentUserProvider currentUserProvider;

    public TenantContext(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * @throws IllegalStateException if called outside an authenticated request —
     *         every authenticated principal has an organization by construction
     *         (see {@code identity.entity.User}), so this signals a programming
     *         error (e.g. calling this from an unprotected endpoint), not a
     *         normal/expected condition.
     */
    public UUID currentOrganizationId() {
        return currentUserProvider.getCurrentUser()
                .map(UserPrincipal::organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated tenant context is available for this request"));
    }
}
