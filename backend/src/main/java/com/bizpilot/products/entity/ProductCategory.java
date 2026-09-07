package com.bizpilot.products.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A product category (CLAUDE.md §13). Deliberately minimal — CLAUDE.md gives
 * no field list for categories at all, so this is an implementation decision
 * (see docs/database.md): just a display name, mirroring
 * {@code organization.entity.Organization}'s equally minimal design. No
 * description field was added — nothing in CLAUDE.md or the product catalog
 * requirements calls for one, and adding it would be an unrequested field.
 *
 * <p>Tenant-scoped: every category belongs to exactly one {@link Organization},
 * assigned once at creation and never changed — same convention as every
 * other tenant-scoped entity in this project.
 */
@Entity
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "name", nullable = false)
    private String name;

    protected ProductCategory() {
        // required by JPA
    }

    public ProductCategory(Organization organization, String name) {
        this.organization = organization;
        this.name = name;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
