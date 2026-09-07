package com.bizpilot.documents.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the upload transaction to background processing (project
 * instructions §12/§13). {@code AFTER_COMMIT} guarantees the listener never
 * fires for a document row that a rolled-back upload never actually
 * persisted. {@link DocumentProcessingService#process} is itself {@code
 * @Async} (not this method) — see its Javadoc for why the async boundary
 * lives there rather than here, which also lets the recovery scheduler
 * reuse the exact same bounded-executor dispatch without needing its own
 * transaction to publish through.
 */
@Component
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DocumentProcessingListener {

    private final DocumentProcessingService documentProcessingService;

    public DocumentProcessingListener(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        documentProcessingService.process(event.documentId());
    }
}
