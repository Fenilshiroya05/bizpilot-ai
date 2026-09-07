package com.bizpilot.identity.repository;

import com.bizpilot.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Tenant-safe lookup for validating that a candidate assignee (e.g. Lead
     * assignment, Phase 8) actually belongs to the caller's organization —
     * never trust a user id without this check (CLAUDE.md §7).
     */
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
