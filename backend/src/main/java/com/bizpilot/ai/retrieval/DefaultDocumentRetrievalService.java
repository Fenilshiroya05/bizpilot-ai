package com.bizpilot.ai.retrieval;

import com.bizpilot.ai.exception.AiProviderException;
import com.bizpilot.organization.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
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
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DefaultDocumentRetrievalService implements DocumentRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentRetrievalService.class);

    private final VectorStore vectorStore;
    private final TenantContext tenantContext;

    public DefaultDocumentRetrievalService(VectorStore vectorStore, TenantContext tenantContext) {
        this.vectorStore = vectorStore;
        this.tenantContext = tenantContext;
    }

    @Override
    public List<RetrievedChunk> search(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }

        // Fail-closed: propagates IllegalStateException, unmodified, before
        // any vector operation is attempted — see class Javadoc.
        UUID organizationId = tenantContext.currentOrganizationId();

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression tenantFilter = b.eq("organizationId", organizationId.toString()).build();

        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .filterExpression(tenantFilter)
                .build();

        Instant start = Instant.now();
        try {
            List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);
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
