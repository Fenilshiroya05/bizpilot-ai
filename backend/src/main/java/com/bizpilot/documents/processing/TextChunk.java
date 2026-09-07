package com.bizpilot.documents.processing;

/**
 * A single chunk of extracted text, with its position within the document.
 * Deliberately not a Spring AI {@code Document} — that type is confined to
 * {@link DocumentChunkingService} and {@code ai.vectorstore}/{@code
 * ai.retrieval} internals, never surfaced across the {@code documents}/
 * {@code ai} module boundary (also avoids a name collision with {@code
 * com.bizpilot.documents.entity.Document}).
 */
public record TextChunk(int index, String text) {
}
