package com.bizpilot.ai.service;

import com.bizpilot.ai.exception.AiProviderException;

import java.util.List;

/**
 * The only surface BizPilot business code should depend on for embedding
 * generation (CLAUDE.md §5) — Phase 14 established the single-text {@link
 * #embed(String)} foundation; Phase 15 (RAG + PGVector) adds {@link
 * #embedBatch(List)} so document processing never issues one OpenAI call
 * per chunk (project instructions §28/§29).
 */
public interface AiEmbeddingService {

    /**
     * Embeds {@code text} using the configured embedding model.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason
     */
    float[] embed(String text);

    /**
     * Embeds every element of {@code texts}, in order, batching internally
     * so a document with many chunks costs a small, bounded number of
     * provider requests rather than one per chunk. All-or-nothing: any
     * batch failure fails the whole call (see {@link
     * com.bizpilot.documents.processing.DocumentProcessingService}, which
     * relies on this to keep document processing itself all-or-nothing).
     *
     * @throws AiProviderException if any underlying batch call fails
     */
    List<float[]> embedBatch(List<String> texts);
}
