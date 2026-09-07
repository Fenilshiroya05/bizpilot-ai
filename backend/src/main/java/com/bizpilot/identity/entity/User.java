package com.bizpilot.identity.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/**
 * A BizPilot AI user account.
 *
 * <p>Tenant-scoped (Phase 5): every user belongs to exactly one
 * {@link Organization}, assigned once at creation and never changed in this
 * phase (no "move user to another org" feature exists yet). {@code email} is
 * still unique globally rather than per-organization — see docs/database.md.
 *
 * <p>RBAC (Phase 6): roles are now a many-to-many relationship to {@link Role}
 * via {@code user_roles} — the single authoritative source, replacing the
 * Phase 4 {@code users.role} column (dropped in V4; see docs/database.md).
 * The schema technically permits multiple roles per user, but nothing in
 * this phase assigns more than one — every user still has exactly one role,
 * just stored in the CLAUDE.md-specified junction-table model instead of a
 * single column.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash, String firstName, String lastName,
                UserStatus status, Organization organization, Role initialRole) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.organization = organization;
        this.roles.add(initialRole);
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    /** Changes the account's status (e.g. an admin disabling/re-enabling a user). */
    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
