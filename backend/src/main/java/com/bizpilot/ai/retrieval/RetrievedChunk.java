package com.bizpilot.ai.retrieval;

import java.util.UUID;

/**
 * One similarity-search result, already stripped of anything a caller
 * shouldn't see (no raw embedding array, no internal database detail —
 * project instructions §27). {@code score} is Spring AI's own similarity
 * score for this result, when available.
 */
public record RetrievedChunk(UUID documentId, UUID chunkId, int chunkIndex, String content,
                              String originalFilename, String contentType, Double score) {
}
