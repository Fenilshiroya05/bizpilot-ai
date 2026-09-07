package com.bizpilot.documents.repository;

import com.bizpilot.documents.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Tenant-safe lookup, used by tests/verification — mirrors the
     * mandatory {@code findByIdAndOrganizationId} pattern used everywhere
     * else, generalized to a to-many relationship.
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.document.id = :documentId AND c.organization.id = :organizationId")
    List<DocumentChunk> findByDocumentAndOrganization(@Param("documentId") UUID documentId,
                                                       @Param("organizationId") UUID organizationId);

    /**
     * Idempotent-rebuild cleanup (Phase 15 §34): called unconditionally at
     * the start of every processing attempt, first-run or reprocess alike,
     * before inserting fresh rows — a delete matching zero rows is a
     * harmless no-op. Organization is included, not just document, for the
     * same defense-in-depth reason vector deletion requires both (see
     * {@code com.bizpilot.ai.vectorstore.DocumentVectorStoreService}).
     */
    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId AND c.organization.id = :organizationId")
    void deleteByDocumentAndOrganization(@Param("documentId") UUID documentId,
                                          @Param("organizationId") UUID organizationId);
}
