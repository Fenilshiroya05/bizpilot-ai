package com.bizpilot.security;

import com.bizpilot.organization.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only endpoints (never shipped — lives under src/test/java) used solely
 * to prove authorization is wired correctly end to end: JWT authority claims
 * -> SecurityContext authorities -> method security decision, for both
 * role-based (@PreAuthorize hasRole) and permission-based (hasAuthority)
 * checks. Mirrors the test-only-fixture pattern used for BaseEntity in
 * Phase 3 (see SampleEntity) rather than adding a placeholder business
 * endpoint to production code.
 */
@RestController
@RequestMapping("/api/v1/test")
public class RoleProtectedTestController {

    private final TenantContext tenantContext;

    public RoleProtectedTestController(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "ok";
    }

    /**
     * Permission-based check on a permission every seeded role has
     * (CUSTOMER_READ), and also returns the tenant-resolved organization id —
     * used by the mandatory "permission granted does not bypass tenant
     * isolation" test (a permission never provides an alternate path to
     * another organization's data).
     */
    @GetMapping("/customer-read-only")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Map<String, String> customerReadOnly() {
        return Map.of("organizationId", tenantContext.currentOrganizationId().toString());
    }

    /**
     * Permission-based check on a permission NOT every seeded role has
     * (MANAGER/ADMIN/OWNER yes, SALES/EMPLOYEE no) — used to prove the
     * allowed-vs-forbidden contrast with a real seeded role/permission pair,
     * rather than an artificial one.
     */
    @GetMapping("/customer-delete-only")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    public String customerDeleteOnly() {
        return "ok";
    }
}
