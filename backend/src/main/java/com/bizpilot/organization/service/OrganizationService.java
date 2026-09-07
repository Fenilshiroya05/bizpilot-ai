package com.bizpilot.organization.service;

import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TenantContext tenantContext;

    public OrganizationService(OrganizationRepository organizationRepository, TenantContext tenantContext) {
        this.organizationRepository = organizationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public Organization create(String name) {
        return organizationRepository.save(new Organization(name.trim()));
    }

    /**
     * Resolves the organization for the current request from the authenticated
     * identity ({@link TenantContext}) — never from a client-supplied id, so
     * there is no method here that accepts an arbitrary organization id from
     * a controller (CLAUDE.md §7).
     */
    @Transactional(readOnly = true)
    public Organization getCurrentOrganization() {
        UUID organizationId = tenantContext.currentOrganizationId();
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated organization no longer exists: " + organizationId));
    }
}
