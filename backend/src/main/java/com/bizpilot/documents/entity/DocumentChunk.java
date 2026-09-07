package com.bizpilot.documents.entity;

import com.bizpilot.common.persistence.BaseEntity;
import com.bizpilot.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The relational-integrity side of a RAG chunk (Phase 15, CLAUDE.md §6/§17).
 * Deliberately does NOT hold chunk text or the embedding vector — both
 * remain the sole responsibility of Spring AI's {@code vector_store} table,
 * managed exclusively through {@code com.bizpilot.ai.vectorstore.DocumentVectorStoreService}.
 *
 * <p>This table exists purely so every chunk is tied, by a real
 * {@code NOT NULL} foreign key, to exactly one {@link Organization} and one
 * {@link Document} — a guarantee {@code vector_store}'s own JSON metadata
 * column cannot give at the database-schema level (see docs/security.md
 * §3m). {@link #getId()} is always the SAME UUID as the corresponding
 * {@code vector_store} row's {@code id} — the join key between the two
 * tables — assigned by Hibernate's {@code GenerationType.UUID} generator
 * (before-execution: available via {@link #getId()} immediately after
 * {@code save()}, before the transaction commits) and reused verbatim when
 * inserting the matching {@code vector_store} row.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    protected DocumentChunk() {
        // required by JPA
    }

    public DocumentChunk(Organization organization, Document document, int chunkIndex, String contentType) {
        this.organization = organization;
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.contentType = contentType;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Document getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContentType() {
        return contentType;
    }
}
