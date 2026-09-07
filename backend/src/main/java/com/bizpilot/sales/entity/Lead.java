package com.bizpilot.sales.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sales lead (CLAUDE.md §11). Tenant-scoped: every lead belongs to exactly
 * one {@link Organization}, assigned once at creation and never changed —
 * same immutability convention as {@code identity.entity.User} and
 * {@code crm.entity.Customer}.
 *
 * <p><b>Identifying fields</b> — CLAUDE.md defines no field list for leads
 * (unlike Customer, §10). Deliberately minimal, not a copy of Customer's
 * field set: {@code name} (required — the only way to identify who/what the
 * lead is) plus {@code company}, {@code email}, {@code phone} (all optional
 * contact/context fields). No {@code address} or {@code gstin}: those are
 * customer/tax-account concerns that don't apply to an unqualified lead, and
 * collecting them here would violate "do not unnecessarily collect personal
 * information." There is deliberately no {@code notes} field on the entity
 * itself (unlike Customer) — "Notes" (§11) is satisfied entirely by
 * {@link LeadActivity} (type {@code NOTE}), avoiding the same
 * field-vs-activity-feature duplication Customer has.
 *
 * <p><b>Status vs. archive</b> — {@link #status} is the fixed CLAUDE.md §11
 * business-outcome enum ({@link LeadStatus}); {@link #archivedAt} is a
 * completely separate, orthogonal record-lifecycle concept (a lead can be
 * {@code WON} or {@code LOST} and still not be archived). This split exists
 * specifically because {@code status} is a closed enum with no
 * {@code ARCHIVED} value permitted, so archiving cannot be modeled as a
 * status transition the way it was for {@code Customer} in Phase 7.
 *
 * <p><b>Assignment</b> — {@code assignedToUserId} is a plain {@link UUID}
 * column (DB-level FK to {@code users}, no JPA relationship) — an
 * attribution-style reference, same pattern as
 * {@code CustomerActivity.createdByUserId}; nothing in this phase needs to
 * load the full {@code User} entity just to know who a lead is assigned to.
 */
@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "company")
    private String company;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private LeadSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private LeadPriority priority;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Lead() {
        // required by JPA
    }

    public Lead(Organization organization, String name, String company, String email, String phone,
                LeadSource source, LeadPriority priority, LocalDate followUpDate) {
        this.organization = organization;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.status = LeadStatus.NEW;
        this.source = source;
        this.priority = priority;
        this.followUpDate = followUpDate;
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

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }

    public LeadSource getSource() {
        return source;
    }

    public void setSource(LeadSource source) {
        this.source = source;
    }

    public LeadPriority getPriority() {
        return priority;
    }

    public void setPriority(LeadPriority priority) {
        this.priority = priority;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(UUID assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
