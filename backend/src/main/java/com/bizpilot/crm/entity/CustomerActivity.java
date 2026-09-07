package com.bizpilot.crm.entity;

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
 * A single entry in a customer's activity/note/history timeline (CLAUDE.md
 * §10). One table backs all three features via {@link CustomerActivityType}
 * — see that enum's Javadoc — deliberately avoiding a separate table per
 * feature and avoiding any generic, project-wide audit/event-sourcing system
 * (that is the distinct, not-yet-built Audit Logging capability, CLAUDE.md
 * §24, which is intentionally out of scope here).
 *
 * <p>Immutable by convention: entries are only ever inserted, never updated
 * or deleted, by {@code CustomerService} (append-only timeline).
 *
 * <p>Tenant safety: this table has no {@code organization_id} of its own —
 * same precedent as {@code security.entity.RefreshToken} (a child of a
 * tenant-scoped parent). Every access path resolves the parent
 * {@link Customer} via the org-scoped {@code findByIdAndOrganizationId}
 * lookup *first*; only then is {@code customer_id} used to query this table,
 * so cross-tenant access is structurally impossible without ever needing to
 * duplicate {@code organization_id} here.
 *
 * <p>{@code createdByUserId} is stored as a plain UUID column (DB-level FK to
 * {@code users}, no JPA relationship) rather than a {@code @ManyToOne User} —
 * it is an attribution stamp only; nothing in this phase needs to load the
 * full {@code User} entity just to render an activity entry.
 */
@Entity
@Table(name = "customer_activities")
public class CustomerActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private CustomerActivityType type;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    protected CustomerActivity() {
        // required by JPA
    }

    public CustomerActivity(Customer customer, CustomerActivityType type, String content, UUID createdByUserId) {
        this.customer = customer;
        this.type = type;
        this.content = content;
        this.createdByUserId = createdByUserId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CustomerActivityType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
