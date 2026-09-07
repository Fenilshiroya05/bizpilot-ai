package com.bizpilot.sales.entity;

import com.bizpilot.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single entry in a lead's activity/note/history timeline (CLAUDE.md §11).
 * Mirrors {@code crm.entity.CustomerActivity} (Phase 7) exactly: one table
 * backs all three features via {@link LeadActivityType}, immutable
 * (insert-only) by convention, and deliberately not the still-unbuilt,
 * project-wide Audit Logging capability (CLAUDE.md §24).
 *
 * <p>Tenant safety: no {@code organization_id} column of its own — every
 * access path resolves the parent {@link Lead} via the org-scoped
 * {@code findByIdAndOrganizationId} lookup first, so {@code lead_id} alone
 * is always already tenant-safe by the time this table is queried (same
 * precedent as {@code CustomerActivity} and {@code security.entity.RefreshToken}).
 */
@Entity
@Table(name = "lead_activities")
public class LeadActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false, updatable = false)
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private LeadActivityType type;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    protected LeadActivity() {
        // required by JPA
    }

    public LeadActivity(Lead lead, LeadActivityType type, String content, UUID createdByUserId) {
        this.lead = lead;
        this.type = type;
        this.content = content;
        this.createdByUserId = createdByUserId;
    }

    public Lead getLead() {
        return lead;
    }

    public LeadActivityType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
