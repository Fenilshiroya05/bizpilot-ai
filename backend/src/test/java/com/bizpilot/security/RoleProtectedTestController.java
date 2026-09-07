package com.bizpilot.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint (never shipped — lives under src/test/java) used solely
 * to prove role-based method security (@PreAuthorize) is wired correctly end
 * to end: JWT role claim -> SecurityContext authority -> method security
 * decision. Mirrors the test-only-fixture pattern used for BaseEntity in
 * Phase 3 (see SampleEntity) rather than adding a placeholder business
 * endpoint to production code.
 */
@RestController
@RequestMapping("/api/v1/test")
public class RoleProtectedTestController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "ok";
    }
}
