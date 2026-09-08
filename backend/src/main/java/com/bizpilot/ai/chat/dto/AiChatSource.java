package com.bizpilot.ai.chat.dto;

import java.util.UUID;

/**
 * One document referenced in an {@link AiChatResponse}'s answer — built
 * entirely from a {@code RetrievedChunk} the backend itself retrieved
 * (project instructions §23/§59); never derived from, or verified against,
 * anything the model outputs. Deliberately excludes chunk content, the
 * embedding vector, any internal vector-store id, and the similarity score
 * — none of which a client needs and none of which should be exposed
 * (project instructions §18/§61).
 */
public record AiChatSource(UUID documentId, String documentName, int chunkIndex) {
}
