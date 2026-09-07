package com.bizpilot.ai.vectorstore;

import com.bizpilot.ai.exception.AiProviderException;

import java.util.List;
import java.util.UUID;

/**
 * The only surface that writes to or deletes from Spring AI's {@code
 * vector_store} table (Phase 15, CLAUDE.md §17) — {@code PgVectorStore}
 * itself is never exposed to {@code documents.processing} or any other
 * module directly (project instructions §24). {@code
 * ai.retrieval.DocumentRetrievalService} is the equivalent, separate,
 * read-only surface for similarity search.
 *
 * <p><b>Both {@code organizationId} and {@code documentId} must always come
 * from server-side state the caller already trusts</b> (the authenticated
 * tenant context for a request-driven delete, or the persisted {@code
 * Document} row's own organization for a background processing delete) —
 * never from client input. This interface has no method that accepts a raw
 * filter expression.
 */
public interface DocumentVectorStoreService {

    /**
     * Persists {@code chunks} (each already embedded) as rows tagged with
     * {@code organizationId}/{@code documentId} metadata, sufficient for
     * {@code DocumentRetrievalService}'s mandatory tenant filter to work.
     *
     * @throws AiProviderException if the underlying vector store write fails
     */
    void store(UUID organizationId, UUID documentId, String originalFilename, String contentType,
               List<VectorChunk> chunks);

    /**
     * Deletes every vector row belonging to {@code documentId} — scoped by
     * BOTH {@code organizationId} and {@code documentId} (project
     * instructions §35: never by {@code documentId} alone), so a mismatched
     * or forged document id can never cause another organization's vectors
     * to be touched. Idempotent: deleting when nothing matches is a no-op.
     *
     * @throws AiProviderException if the underlying vector store delete fails
     */
    void deleteForDocument(UUID organizationId, UUID documentId);
}
