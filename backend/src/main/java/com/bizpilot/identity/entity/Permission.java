package com.bizpilot.identity.entity;

import com.bizpilot.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A granular permission (e.g. {@code CUSTOMER_READ}) — CLAUDE.md §9. The
 * catalog is seeded by Flyway (V4); this entity is read-only from the
 * application's point of view in Phase 6 (no permission-management API).
 */
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    protected Permission() {
        // required by JPA
    }

    public Permission(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
