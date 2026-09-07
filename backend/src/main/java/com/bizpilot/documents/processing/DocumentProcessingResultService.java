package com.bizpilot.documents.processing;

import com.bizpilot.ai.vectorstore.DocumentVectorStoreService;
import com.bizpilot.ai.vectorstore.VectorChunk;
import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.entity.DocumentChunk;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.documents.repository.DocumentChunkRepository;
import com.bizpilot.documents.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns the two short, separate database transactions a processing attempt
 * ends in (project instructions §16/§33) — deliberately a SEPARATE bean
 * from {@link DocumentProcessingService} (which is {@code @Async} and calls
 * these methods across a real Spring proxy boundary; a self-invoked {@code
 * @Transactional} method on the same class would silently run with no
 * transaction at all, since Spring's proxy-based AOP cannot intercept
 * internal calls). Extraction, chunking, and the OpenAI embedding call all
 * happen in {@code DocumentProcessingService} itself, outside of any
 * transaction — never inside either method here.
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DocumentProcessingResultService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingResultService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentVectorStoreService documentVectorStoreService;

    public DocumentProcessingResultService(DocumentRepository documentRepository,
                                            DocumentChunkRepository documentChunkRepository,
                                            DocumentVectorStoreService documentVectorStoreService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentVectorStoreService = documentVectorStoreService;
    }

    /**
     * "Transaction 2": clears any previous chunk/vector data for this
     * document (idempotent-rebuild — project instructions §34, harmless
     * no-op on a first-ever attempt), persists the new {@code
     * document_chunks} rows (each one's Hibernate-generated id is reused
     * verbatim as its corresponding {@code vector_store} row's id — the
     * join key between the two tables, project instructions §9), inserts
     * the new vectors, and marks the document {@code COMPLETED} — all in
     * one transaction, so a failure at any step leaves the PREVIOUS
     * successful chunks/vectors (if any) intact and the document {@code
     * PROCESSING} until the outer catch-block's separate {@link
     * #markFailed} transaction runs.
     *
     * <p>All-or-nothing (project instructions §32): there is no path that
     * marks the document {@code COMPLETED} before every chunk and every
     * vector has been persisted.
     */
    @Transactional
    public void persistSuccess(UUID documentId, UUID organizationId, String originalFilename, String contentType,
                                List<TextChunk> chunks, List<float[]> embeddings) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document disappeared mid-processing, this should be unreachable: " + documentId));

        documentChunkRepository.deleteByDocumentAndOrganization(documentId, organizationId);
        documentVectorStoreService.deleteForDocument(organizationId, documentId);

        List<VectorChunk> vectorChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            DocumentChunk saved = documentChunkRepository.save(
                    new DocumentChunk(document.getOrganization(), document, chunk.index(), contentType));
            // GenerationType.UUID is a before-execution generator — the id
            // is already assigned in-memory here, before this transaction
            // even flushes, let alone commits.
            vectorChunks.add(new VectorChunk(saved.getId(), chunk.index(), chunk.text(), embeddings.get(i)));
        }

        documentVectorStoreService.store(organizationId, documentId, originalFilename, contentType, vectorChunks);

        document.setStatus(DocumentStatus.COMPLETED);
        documentRepository.save(document);

        log.info("Document processing completed [documentId={}, chunkCount={}]", documentId, chunks.size());
    }

    @Transactional
    public void markFailed(UUID documentId, Exception cause) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        });
        log.error("Document processing failed [documentId={}, errorType={}]",
                documentId, cause.getClass().getSimpleName(), cause);
    }
}
