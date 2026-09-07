package com.bizpilot.documents.entity;

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

import java.util.UUID;

/**
 * Uploaded document metadata (CLAUDE.md §16). Tenant-scoped: every document
 * belongs to exactly one {@link Organization}, assigned once at creation and
 * never changed — same convention as every other tenant-scoped entity.
 *
 * <p><b>Standalone module, no business-entity associations.</b> CLAUDE.md
 * §16 names no relationship to Customer/Lead/Quotation/Invoice/Task
 * anywhere — unlike Task (§23), which explicitly named "Related
 * customer"/"Related lead." A document here is purely an
 * organization-owned file with no other foreign reference at all.
 *
 * <p><b>Binary content is never stored here or anywhere in the database</b>
 * (CLAUDE.md §16, explicit prohibition) — only {@link #storageKey}, an
 * opaque, server-generated locator resolved through
 * {@code documents.service.DocumentStorageService}. {@code storageKey} is
 * never derived from {@link #originalFilename} and never returned to a
 * client (see {@code documents.dto.DocumentResponse}).
 *
 * <p><b>{@code originalFilename} is inert metadata only</b> — sanitized at
 * upload time ({@code documents.service.DocumentValidator.sanitizeFilename})
 * and never used to construct a filesystem path; the physical location is
 * determined entirely by {@link #storageKey}.
 *
 * <p><b>{@code uploadedByUserId} is a plain {@link UUID} column</b>, not a
 * JPA relationship — the same attribution-style-reference pattern already
 * established by {@code sales.entity.Lead.assignedToUserId}: nothing here
 * ever needs to load the full {@code User} entity.
 */
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "uploaded_by_user_id", updatable = false)
    private UUID uploadedByUserId;

    protected Document() {
        // required by JPA
    }

    public Document(Organization organization, String originalFilename, String storageKey,
                     String contentType, long fileSize, UUID uploadedByUserId) {
        this.organization = organization;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = DocumentStatus.UPLOADED;
        this.uploadedByUserId = uploadedByUserId;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    /** Reserved for the Phase 15 processing pipeline — never called anywhere in Phase 13. */
    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public UUID getUploadedByUserId() {
        return uploadedByUserId;
    }
}
