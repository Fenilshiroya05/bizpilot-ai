package com.bizpilot.documents.processing;

import com.bizpilot.documents.config.DocumentProcessingProperties;
import com.bizpilot.documents.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recovers documents stuck in {@code UPLOADED} (the {@code AFTER_COMMIT}
 * event was lost — e.g. an application restart between commit and listener
 * execution) or {@code PROCESSING} (a crash mid-pipeline) — the mitigation
 * for the non-durable, in-JVM event trigger's known limitation (project
 * instructions §70/§39; see docs/security.md).
 *
 * <p>Deliberately spans every organization in one pass — this is a
 * system-level maintenance job, not a per-tenant request, so it has no
 * {@code TenantContext} to resolve and none is needed: each recovered
 * document is reprocessed through {@link DocumentProcessingService#process},
 * which derives its own organization id from the document row itself (see
 * that class's Javadoc), exactly as it already does for the initial-upload
 * trigger. {@code transitionToProcessing}'s atomic {@code WHERE} clause is
 * what prevents this sweep from ever double-processing a document another
 * worker (or a concurrent sweep run) has already claimed (project
 * instructions §41) — this class adds no additional locking of its own.
 */
@Component
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DocumentRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentRecoveryScheduler.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentProcessingProperties properties;

    public DocumentRecoveryScheduler(DocumentRepository documentRepository,
                                      DocumentProcessingService documentProcessingService,
                                      DocumentProcessingProperties properties) {
        this.documentRepository = documentRepository;
        this.documentProcessingService = documentProcessingService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${bizpilot.documents.processing.recovery-interval}")
    public void recoverStaleDocuments() {
        Instant now = Instant.now();
        Instant staleUploadedBefore = now.minus(properties.staleUploadedThreshold());
        Instant staleProcessingBefore = now.minus(properties.staleProcessingThreshold());

        List<UUID> staleDocumentIds = documentRepository.findStaleDocumentIds(staleUploadedBefore, staleProcessingBefore);
        if (staleDocumentIds.isEmpty()) {
            return;
        }
        log.info("Recovery sweep found {} stale document(s) to reprocess", staleDocumentIds.size());
        for (UUID documentId : staleDocumentIds) {
            try {
                documentProcessingService.process(documentId);
            } catch (RuntimeException e) {
                // process() itself already catches and handles pipeline
                // failures internally (transitioning the document to
                // FAILED) — this only guards against a failure to even
                // dispatch the async call itself, so one bad document id
                // never aborts the rest of the sweep.
                log.error("Recovery sweep failed to dispatch reprocessing for a stale document [documentId={}]",
                        documentId, e);
            }
        }
    }
}
