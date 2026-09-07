package com.bizpilot.organization.controller;

import com.bizpilot.organization.dto.OrganizationResponse;
import com.bizpilot.organization.mapper.OrganizationMapper;
import com.bizpilot.organization.service.OrganizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately minimal for Phase 5: the only endpoint is "get my current
 * organization," resolved entirely from the authenticated identity. There is
 * no {@code GET /organizations/{id}} — that would require deciding
 * cross-tenant read authorization rules that don't yet exist (RBAC is Phase 6),
 * and no endpoint here accepts an organization id from the client at all.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;

    public OrganizationController(OrganizationService organizationService, OrganizationMapper organizationMapper) {
        this.organizationService = organizationService;
        this.organizationMapper = organizationMapper;
    }

    @GetMapping("/current")
    public OrganizationResponse current() {
        return organizationMapper.toResponse(organizationService.getCurrentOrganization());
    }
}
