package com.bizpilot.ai.service;

import com.bizpilot.ai.exception.AiProviderException;

/**
 * The only surface future BizPilot business code should depend on for
 * embedding generation (CLAUDE.md §5) — foundation only. Nothing in this
 * phase persists a vector, chunks a document, or talks to PGVector; this
 * exists solely so Phase 15 (RAG + PGVector) has a working, tested
 * {@code EmbeddingModel} wrapper to build on, per project instructions §8.
 */
public interface AiEmbeddingService {

    /**
     * Embeds {@code text} using the configured embedding model.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason
     */
    float[] embed(String text);
}
