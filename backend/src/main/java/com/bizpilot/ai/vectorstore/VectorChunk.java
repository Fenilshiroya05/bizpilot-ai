package com.bizpilot.ai.vectorstore;

import java.util.UUID;

/**
 * One chunk's already-computed content and embedding, ready to persist.
 * {@code chunkId} must be the SAME UUID as the corresponding {@code
 * document_chunks} row (see {@code documents.processing.DocumentProcessingService})
 * — the join key between the relational-integrity table and Spring AI's
 * {@code vector_store} table.
 */
public record VectorChunk(UUID chunkId, int chunkIndex, String text, float[] embedding) {
}
