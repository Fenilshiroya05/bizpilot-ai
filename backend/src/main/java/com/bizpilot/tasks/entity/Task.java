package com.bizpilot.tasks.entity;

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

import java.time.LocalDate;
import java.util.UUID;

/**
 * A task (CLAUDE.md §23). Tenant-scoped: every task belongs to exactly one
 * {@link Organization}, assigned once at creation and never changed — same
 * convention as every other tenant-scoped entity in this project.
 *
 * <p><b>First entity in its own top-level module.</b> Unlike Quotations/
 * Invoices (explicitly folded into {@code sales} by their own phase
 * instructions), CLAUDE.md §42 gives Tasks its own top-level package
 * (alongside {@code documents}/{@code ai}/{@code analytics}/{@code notifications}/
 * {@code audit}), so {@code Task} lives in {@code tasks.entity}, not
 * {@code sales.entity} or {@code crm.entity}.
 *
 * <p><b>Assignee/customer/lead are plain {@link UUID} columns, not JPA
 * relationships</b> — the exact same attribution-style-reference pattern
 * already established by {@code sales.entity.Lead.assignedToUserId}: nothing
 * in this module ever needs to load the full {@code User}/{@code Customer}/
 * {@code Lead} entity just to know which one a task references, and a
 * {@code Task} never renders/calculates anything from them the way
 * {@code Quotation}/{@code Invoice} do from their line items' products. This
 * also means {@code Task} has no cross-module JPA {@code @ManyToOne} at all
 * beyond {@code Organization} — simpler than Quotation/Invoice, not by
 * omission but because nothing here requires the richer relationship.
 *
 * <p><b>No immutability.</b> Unlike {@code Invoice}, a {@code Task} remains
 * fully editable at every status, including {@code COMPLETED}/
 * {@code CANCELLED} (an explicit, approved Phase 12 decision) — reopening a
 * completed or cancelled task back to {@code TODO}/{@code IN_PROGRESS} is a
 * normal, supported update, not a special case.
 *
 * <p><b>No separate notes/activity table.</b> CLAUDE.md §23 lists a single
 * "Notes" feature bullet with no accompanying "Task activities"/"Task
 * history" bullet (contrast {@code Lead}/{@code Customer}, which each list
 * both) — so {@code notes} here is a single plain text field, exactly like
 * {@code crm.entity.Customer.notes}, not a separate history-log entity.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TaskPriority priority;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "notes")
    private String notes;

    protected Task() {
        // required by JPA
    }

    public Task(Organization organization, String title, String description, TaskPriority priority,
                LocalDate dueDate, UUID customerId, UUID leadId) {
        this.organization = organization;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.priority = priority;
        this.dueDate = dueDate;
        this.customerId = customerId;
        this.leadId = leadId;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(UUID assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isCancelled() {
        return status == TaskStatus.CANCELLED;
    }
}
