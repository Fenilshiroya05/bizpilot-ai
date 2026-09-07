package com.bizpilot.ai.vectorstore;

import com.bizpilot.ai.exception.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link DocumentVectorStoreService} implementation.
 *
 * <p><b>Writes bypass {@link VectorStore#add}, deliberately.</b> Spring AI's
 * {@code PgVectorStore.doAdd} always computes its own embeddings internally
 * (via its own configured {@code EmbeddingModel}, ignoring any externally
 * supplied vector) — verified directly against the 1.1.8 source, not
 * assumed. Since project instructions §28/§29 require ALL embedding calls to
 * go through {@code AiEmbeddingService.embedBatch} (so batching, logging,
 * and {@code AiProviderException} wrapping are consistent and so a document
 * is never embedded twice), this class inserts the already-computed vectors
 * with a plain, verified-against-source-compatible {@code INSERT} —
 * matching {@code PgVectorStore}'s own schema/statement shape exactly (same
 * table, same column names/order, same {@code ?::jsonb} cast, same {@link
 * PGvector} JDBC binding) so the rows it writes are fully readable by {@code
 * PgVectorStore.similaritySearch}/{@code delete} afterwards. Only the WRITE
 * path is reimplemented this way — similarity search and delete (the
 * genuinely hard part: cosine-distance SQL, HNSW index usage) still go
 * entirely through {@link VectorStore}, per project instructions §59.
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DefaultDocumentVectorStoreService implements DocumentVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentVectorStoreService.class);

    /**
     * Matches Spring AI PgVectorStore's own default {@code public.vector_store}
     * location exactly (never overridden — see application.yml) and its own
     * verified INSERT statement shape (id, content, metadata::jsonb, embedding),
     * with an {@code ON CONFLICT} upsert for the same defensive reason
     * PgVectorStore's own insert has one.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO public.vector_store (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?)
            ON CONFLICT (id) DO UPDATE SET content = ?, metadata = ?::jsonb, embedding = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public DefaultDocumentVectorStoreService(JdbcTemplate jdbcTemplate, VectorStore vectorStore,
                                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(UUID organizationId, UUID documentId, String originalFilename, String contentType,
                       List<VectorChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        Instant start = Instant.now();
        try {
            jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    VectorChunk chunk = chunks.get(i);
                    String metadataJson = toJson(metadataFor(organizationId, documentId, originalFilename,
                            contentType, chunk.chunkIndex()));
                    PGvector vector = new PGvector(chunk.embedding());

                    ps.setObject(1, chunk.chunkId());
                    ps.setString(2, chunk.text());
                    ps.setString(3, metadataJson);
                    ps.setObject(4, vector);
                    ps.setString(5, chunk.text());
                    ps.setString(6, metadataJson);
                    ps.setObject(7, vector);
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            });
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Vector store insert succeeded [documentId={}, chunkCount={}, durationMs={}]",
                    documentId, chunks.size(), durationMs);
        } catch (RuntimeException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Vector store insert failed [documentId={}, chunkCount={}, durationMs={}, errorType={}]",
                    documentId, chunks.size(), durationMs, e.getClass().getSimpleName(), e);
            throw new AiProviderException("Vector store insert failed", e);
        }
    }

    @Override
    public void deleteForDocument(UUID organizationId, UUID documentId) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            Filter.Expression tenantAndDocumentFilter = b.and(
                    b.eq("organizationId", organizationId.toString()),
                    b.eq("documentId", documentId.toString())
            ).build();
            vectorStore.delete(tenantAndDocumentFilter);
            log.info("Vector store delete succeeded [organizationId={}, documentId={}]", organizationId, documentId);
        } catch (RuntimeException e) {
            log.error("Vector store delete failed [organizationId={}, documentId={}, errorType={}]",
                    organizationId, documentId, e.getClass().getSimpleName(), e);
            throw new AiProviderException("Vector store delete failed", e);
        }
    }

    private static Map<String, Object> metadataFor(UUID organizationId, UUID documentId, String originalFilename,
                                                     String contentType, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", organizationId.toString());
        metadata.put("documentId", documentId.toString());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("originalFilename", originalFilename);
        metadata.put("contentType", contentType);
        return metadata;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new AiProviderException("Failed to serialize vector metadata", e);
        }
    }
}
