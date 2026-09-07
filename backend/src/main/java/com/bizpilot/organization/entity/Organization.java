package com.bizpilot.organization.entity;

import com.bizpilot.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The tenant root (CLAUDE.md §7): every business belongs to exactly one
 * organization, and every business record will ultimately be scoped to one.
 *
 * <p>Deliberately minimal for Phase 5 — CLAUDE.md does not specify an
 * organization field list, so only a display name is included. Billing,
 * settings, and other business metadata are added only when a later phase
 * explicitly requires them.
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected Organization() {
        // required by JPA
    }

    public Organization(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
