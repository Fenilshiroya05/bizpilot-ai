package com.bizpilot.crm.entity;

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

/**
 * A CRM customer record (CLAUDE.md §10). Tenant-scoped: every customer
 * belongs to exactly one {@link Organization}, assigned once at creation and
 * never changed (same immutability convention as {@code identity.entity.User}).
 *
 * <p>Fields mirror CLAUDE.md §10 exactly: name, company, email, phone,
 * address, GSTIN, status, notes — no additional personal information is
 * collected, per the explicit "do not unnecessarily collect personal
 * information" instruction.
 *
 * <p>{@code notes} here is a single free-text field on the customer record
 * itself (part of the §10 field list). The separate "Customer notes"
 * *feature* — a running, timestamped, author-attributed log of notes added
 * over time — is a different concept, backed by {@link CustomerActivity}
 * (type {@code NOTE}); see docs/database.md for why both exist.
 */
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

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

    @Column(name = "address")
    private String address;

    @Column(name = "gstin")
    private String gstin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "notes")
    private String notes;

    protected Customer() {
        // required by JPA
    }

    public Customer(Organization organization, String name, String company, String email, String phone,
                     String address, String gstin, String notes) {
        this.organization = organization;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.gstin = gstin;
        this.notes = notes;
        this.status = CustomerStatus.ACTIVE;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isArchived() {
        return status == CustomerStatus.ARCHIVED;
    }
}
