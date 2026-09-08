package com.bizpilot.documents.processing;

import com.bizpilot.ai.service.AiEmbeddingService;
import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.exception.DocumentProcessingException;
import com.bizpilot.documents.extraction.TextExtractionService;
import com.bizpilot.documents.repository.DocumentRepository;
import com.bizpilot.documents.service.DocumentStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.bizpilot.documents.config.DocumentProcessingConfig.EXECUTOR_BEAN_NAME;

/**
 * Orchestrates the Phase 15 pipeline (CLAUDE.md §17): extract, chunk,
 * embed, persist. The single public entry point, {@link #process(UUID)},
 * serves as BOTH the initial-upload trigger (called by {@link
 * DocumentProcessingListener}) AND the reprocess/retry operation (project
 * instructions §38: {@code FAILED -> PROCESSING} is exactly the same
 * pipeline, gated by the same atomic transition) and the recovery-sweep
 * trigger ({@link DocumentRecoveryScheduler}) — there is no separate {@code
 * reprocess(UUID)} method, since it would do nothing this one doesn't
 * already do; a second method would only invite the two to drift.
 *
 * <p><b>Deliberate exception to "always look up a tenant-scoped entity via
 * {@code findByIdAndOrganizationId}."</b> This method runs on the bounded
 * async executor (triggered by an event listener or the recovery
 * scheduler) — never inside an authenticated HTTP request — so there is no
 * {@code TenantContext}/{@code SecurityContext} available on this thread to
 * resolve a "current organization" from. {@code documentId} is never
 * caller/client input here (it comes from an internally published event or
 * the recovery sweep's own query), so a plain {@link
 * DocumentRepository#findById} is safe: the organization this document
 * belongs to is read from the row itself ({@code
 * document.getOrganization()}), not assumed or trusted from any external
 * source. Every vector/chunk write that follows uses THAT organization id.
 *
 * <p><b>Transaction boundaries (project instructions §16/§33).</b> Only the
 * atomic {@code UPLOADED|FAILED -> PROCESSING} transition and the final
 * persist-results step are transactional (see {@link
 * DocumentProcessingResultService}, a separate bean so its {@code
 * @Transactional} methods are invoked through a real Spring proxy).
 * Extraction, chunking, and the OpenAI embedding call all happen here, in
 * between, with no open transaction.
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final TextExtractionService textExtractionService;
    private final DocumentChunkingService documentChunkingService;
    private final AiEmbeddingService aiEmbeddingService;
    private final DocumentProcessingResultService documentProcessingResultService;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                      DocumentStorageService documentStorageService,
                                      TextExtractionService textExtractionService,
                                      DocumentChunkingService documentChunkingService,
                                      AiEmbeddingService aiEmbeddingService,
                                      DocumentProcessingResultService documentProcessingResultService) {
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.textExtractionService = textExtractionService;
        this.documentChunkingService = documentChunkingService;
        this.aiEmbeddingService = aiEmbeddingService;
        this.documentProcessingResultService = documentProcessingResultService;
    }

    @Async(EXECUTOR_BEAN_NAME)
    public void process(UUID documentId) {
        // Atomic concurrency guard (project instructions §41) — must run
        // BEFORE any expensive work (storage read, extraction, embedding),
        // and before loading the document's own fields, so a losing race
        // never does any of that work at all.
        int transitioned = documentRepository.transitionToProcessing(documentId);
        if (transitioned == 0) {
            log.info("Skipping document processing — not in a processable state or already claimed "
                    + "by another worker [documentId={}]", documentId);
            return;
        }

        Instant start = Instant.now();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            // Should be unreachable (we just transitioned this exact row),
            // but never assume — nothing to process if it's gone.
            log.warn("Document vanished immediately after being claimed for processing [documentId={}]", documentId);
            return;
        }
        UUID organizationId = document.getOrganization().getId();

        try {
            Resource content = documentStorageService.load(document.getStorageKey());
            String text = textExtractionService.extract(document.getContentType(), content);
            if (text == null || text.isBlank()) {
                throw new DocumentProcessingException("Extracted text is empty or whitespace-only");
            }

            List<TextChunk> chunks = documentChunkingService.split(text);
            List<float[]> embeddings = aiEmbeddingService.embedBatch(chunks.stream().map(TextChunk::text).toList());

            documentProcessingResultService.persistSuccess(documentId, organizationId, document.getOriginalFilename(),
                    document.getContentType(), chunks, embeddings);

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Document processing succeeded [documentId={}, organizationId={}, chunkCount={}, durationMs={}]",
                    documentId, organizationId, chunks.size(), durationMs);
        } catch (RuntimeException e) {
            // Deliberately broad: the whole pipeline (extraction, chunking,
            // embedding, persistence) must reduce to exactly one of two
            // outcomes, COMPLETED or FAILED (project instructions §32) —
            // this is the single required failure funnel, not a swallowed
            // exception (it is always logged, with type and stack trace,
            // and always results in an explicit FAILED transition).
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            // Production-readiness audit finding (post-Phase-19): `e` was
            // previously never passed as the trailing SLF4J argument, so
            // despite this method's own comment claiming "always logged,
            // with type and stack trace," no stack trace was ever actually
            // printed — only the exception's simple class name. Passing `e`
            // here (server-side log only; never reaches any client
            // response) restores that.
            log.error("Document processing failed [documentId={}, organizationId={}, durationMs={}, errorType={}]",
                    documentId, organizationId, durationMs, e.getClass().getSimpleName(), e);
            documentProcessingResultService.markFailed(documentId, e);
        }
    }
}
