package com.bizpilot.ai.retrieval;

import com.bizpilot.ai.exception.AiProviderException;
import com.bizpilot.organization.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link DocumentRetrievalService} implementation, built directly
 * on Spring AI's {@link VectorStore}.
 *
 * <p><b>Fail-closed tenant resolution (project instructions §23) is the
 * very first thing this method does</b> — {@link TenantContext#currentOrganizationId()}
 * itself already throws {@link IllegalStateException} when no authenticated
 * request context is available (the same fail-closed mechanism every other
 * tenant-scoped service in this project already relies on; a second,
 * module-specific exception type would be a redundant abstraction), and
 * that call happens before any vector operation — never a null/placeholder
 * organization id, never a fallback to an unfiltered search.
 *
 * <p><b>The query text is not separately pre-embedded via {@code
 * AiEmbeddingService}.</b> Verified against the 1.1.8 source: {@code
 * PgVectorStore.doSimilaritySearch} always computes the query embedding
 * itself, internally, from {@code SearchRequest.getQuery()} — there is no
 * public API to supply a pre-computed query vector instead. Calling {@code
 * AiEmbeddingService.embed(queryText)} first and then also calling {@code
 * similaritySearch} would silently double the embedding cost/latency of
 * every search for no benefit, so this class passes the raw query text
 * straight to {@link VectorStore#similaritySearch(SearchRequest)} and lets
 * Spring AI's own (still OpenAI-backed, still the same configured
 * EmbeddingModel bean) internal call handle it.
 *
 * <p><b>{@link VectorStore} is held via {@link ObjectProvider}</b>
 * (local-chat-only scope addition) — this class's constructor used to take
 * {@link VectorStore} as a plain, required argument, which would fail
 * application startup outright with a {@code BeanCreationException} in an
 * environment with no vector store configured at all (e.g. local Ollama
 * chat with {@code AI_VECTORSTORE_TYPE=none}, no embedding model). An
 * earlier attempt gated this whole bean with {@code @ConditionalOnBean(VectorStore.class)}
 * instead — that was INCORRECT and has been reverted: Spring Boot evaluates
 * {@code @ConditionalOnBean} against bean DEFINITIONS already registered at
 * the time this plain, component-scanned {@code @Service} is processed,
 * which happens BEFORE {@code PgVectorStoreAutoConfiguration} registers its
 * own {@code VectorStore} bean definition — so the condition incorrectly
 * evaluated as "absent" even in a fully, correctly configured environment,
 * silently breaking retrieval/tenant-isolation everywhere (caught by the
 * existing test suite, not assumed). Holding {@link VectorStore} via {@link
 * ObjectProvider} instead sidesteps bean-creation-order entirely: this bean
 * always exists once {@code bizpilot.ai.enabled=true}, and {@link #search}
 * resolves the vector store lazily, at call time, returning an empty result
 * (exactly like a real zero-match search) when none is configured — never a
 * fabricated/placeholder result, and the fail-closed tenant check below is
 * completely unaffected by whether a vector store exists.
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DefaultDocumentRetrievalService implements DocumentRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentRetrievalService.class);

    private final ObjectProvider<VectorStore> vectorStore;
    private final TenantContext tenantContext;

    public DefaultDocumentRetrievalService(ObjectProvider<VectorStore> vectorStore, TenantContext tenantContext) {
        this.vectorStore = vectorStore;
        this.tenantContext = tenantContext;
    }

    @Override
    public List<RetrievedChunk> search(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }

        // Fail-closed: propagates IllegalStateException, unmodified, before
        // any vector operation is attempted — see class Javadoc. Unconditional,
        // regardless of whether a vector store is configured.
        UUID organizationId = tenantContext.currentOrganizationId();

        // No vector store configured (local-chat-only scope) — nothing to
        // search, same as a real zero-match result; never a fabricated one.
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.info("Vector similarity search skipped — no vector store configured [organizationId={}]", organizationId);
            return List.of();
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression tenantFilter = b.eq("organizationId", organizationId.toString()).build();

        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .filterExpression(tenantFilter)
                .build();

        Instant start = Instant.now();
        try {
            List<org.springframework.ai.document.Document> results = store.similaritySearch(request);
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Vector similarity search succeeded [organizationId={}, topK={}, resultCount={}, durationMs={}]",
                    organizationId, topK, results.size(), durationMs);
            return results.stream().map(DefaultDocumentRetrievalService::toRetrievedChunk).toList();
        } catch (RuntimeException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Vector similarity search failed [organizationId={}, topK={}, durationMs={}, errorType={}]",
                    organizationId, topK, durationMs, e.getClass().getSimpleName(), e);
            throw new AiProviderException("Vector similarity search failed", e);
        }
    }

    private static RetrievedChunk toRetrievedChunk(org.springframework.ai.document.Document document) {
        Map<String, Object> metadata = document.getMetadata();
        UUID documentId = UUID.fromString(String.valueOf(metadata.get("documentId")));
        int chunkIndex = ((Number) metadata.get("chunkIndex")).intValue();
        String originalFilename = String.valueOf(metadata.get("originalFilename"));
        String contentType = String.valueOf(metadata.get("contentType"));
        return new RetrievedChunk(documentId, UUID.fromString(document.getId()), chunkIndex, document.getText(),
                originalFilename, contentType, document.getScore());
    }
}
