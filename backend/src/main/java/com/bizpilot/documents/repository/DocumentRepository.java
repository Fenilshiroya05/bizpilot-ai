package com.bizpilot.documents.repository;

import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * The mandatory tenant-safe lookup (CLAUDE.md §7): never {@code findById}
     * alone for a tenant-scoped entity.
     */
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Atomic {@code UPLOADED|FAILED -> PROCESSING} transition (Phase 15
     * §31/§41) — the sole concurrency-protection mechanism for document
     * processing. A single {@code UPDATE ... WHERE status IN (...)}
     * statement is evaluated atomically by PostgreSQL regardless of how
     * many callers race to invoke it concurrently (the event listener, the
     * recovery sweep, or both for the same document): only one caller's
     * statement can ever see a matching row and flip it to
     * {@code PROCESSING}; every other concurrent caller's {@code WHERE}
     * clause matches zero rows (return value {@code 0}) the instant the
     * first one commits its row lock, and must skip processing — never an
     * in-memory flag, which would not be safe across the multiple JVM
     * threads (async executor + scheduler) this phase introduces.
     *
     * <p>Bulk JPQL {@code UPDATE} statements bypass entity lifecycle
     * callbacks (including {@code @LastModifiedDate} auditing), so
     * {@code updated_at} is bumped explicitly here — {@link #findStaleDocumentIds}
     * depends on this column accurately reflecting when processing started.
     *
     * <p>{@code @Transactional} here, explicitly — unlike {@code save}/
     * {@code delete}/derived-query methods, Spring Data JPA does not
     * implicitly wrap a custom {@code @Modifying} query in its own
     * transaction; it requires one already active on the calling thread.
     * {@code DocumentProcessingService.process} deliberately runs with no
     * ambient transaction of its own (project instructions §16/§33), so
     * this repository method must supply its own short one — "Transaction
     * 1", exactly as designed, just declared here instead of in the caller.
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Document d SET d.status = 'PROCESSING', d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id AND d.status IN ('UPLOADED', 'FAILED')
            """)
    int transitionToProcessing(@Param("id") UUID id);

    /**
     * Recovery-sweep target selection (Phase 15 §39): documents that have
     * been sitting in {@code UPLOADED} (the AFTER_COMMIT event was lost —
     * e.g. an application restart between commit and listener execution)
     * or {@code PROCESSING} (a crash mid-pipeline) for longer than their
     * respective configured staleness threshold. Deliberately spans every
     * organization — this is a system-level maintenance job, not a
     * per-tenant operation, so it has no {@code organizationId} to scope
     * by; each recovered document is then reprocessed through the normal,
     * per-document tenant-safe pipeline (see {@code DocumentProcessingService}).
     */
    @Query("""
            SELECT d.id FROM Document d
            WHERE (d.status = 'UPLOADED' AND d.updatedAt < :staleUploadedBefore)
               OR (d.status = 'PROCESSING' AND d.updatedAt < :staleProcessingBefore)
            """)
    List<UUID> findStaleDocumentIds(@Param("staleUploadedBefore") Instant staleUploadedBefore,
                                     @Param("staleProcessingBefore") Instant staleProcessingBefore);

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
