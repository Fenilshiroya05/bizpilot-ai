package com.bizpilot.ai.chat;

import com.bizpilot.ai.chat.dto.AiChatResponse;
import com.bizpilot.ai.exception.AiDisabledException;
import com.bizpilot.ai.exception.AiProviderException;

/**
 * Orchestrates the Phase 16 RAG-chat flow (CLAUDE.md §18): retrieve →
 * (no results? deterministic answer) → build context → chat → map sources.
 * Deliberately minimal — one method, no conversation state, no tool
 * calling, no business-data access (project instructions §5/§6).
 */
public interface AiAssistantService {

    /**
     * @param message an already-validated (blank/length-checked at the
     *                controller boundary) user question
     * @throws AiDisabledException  if AI is currently disabled — checked
     *                               before any retrieval or provider call
     * @throws IllegalStateException if no authenticated tenant context is
     *                               available (propagated, unmodified, from
     *                               {@code DocumentRetrievalService} — fails
     *                               closed; see {@code DefaultAiAssistantService})
     * @throws AiProviderException  if retrieval or the chat model call fails
     */
    AiChatResponse ask(String message);
}
