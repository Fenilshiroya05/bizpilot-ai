package com.bizpilot.documents.repository;

import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7): never {@code findById}
     * alone for a tenant-scoped entity.
     */
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * All filters are optional (a {@code null} parameter matches every
     * value); pagination and filtering happen entirely at the database
     * level — mirrors {@code sales.repository.LeadRepository.search}/
     * {@code tasks.repository.TaskRepository.search}.
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.organization.id = :organizationId
              AND (:status IS NULL OR d.status = :status)
              AND (:contentType IS NULL OR d.contentType = :contentType)
              AND (:uploadedByUserId IS NULL OR d.uploadedByUserId = :uploadedByUserId)
              AND (:search IS NULL OR LOWER(d.originalFilename) LIKE :search)
            """)
    Page<Document> search(@Param("organizationId") UUID organizationId,
                           @Param("status") DocumentStatus status,
                           @Param("contentType") String contentType,
                           @Param("uploadedByUserId") UUID uploadedByUserId,
                           @Param("search") String search,
                           Pageable pageable);
}
