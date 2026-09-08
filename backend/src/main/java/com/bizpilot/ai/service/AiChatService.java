package com.bizpilot.ai.service;

import com.bizpilot.ai.exception.AiProviderException;

import java.util.List;

/**
 * The only surface future BizPilot business code should depend on for AI
 * chat capability (CLAUDE.md §5) — never {@code org.springframework.ai.*}
 * types directly, and never a specific provider's SDK. Spring AI's own
 * auto-configuration (selected via {@code spring.ai.model.chat}) is what
 * makes the underlying provider swappable; this interface exists purely to
 * keep that Spring AI dependency out of business modules, not to reimplement
 * provider selection.
 *
 * <p>Deliberately minimal (project instructions §7, Phase 14): a single
 * prompt in, a single text response out. Phase 16 added system/user role
 * separation; Phase 17 adds tool calling ({@link #chat(String, String, List)}) —
 * still no conversation history, no memory, no streaming, no RAG (RAG stays
 * {@code ai.retrieval.DocumentRetrievalService}'s own concern, called
 * separately by the assistant, never routed through this interface).
 */
public interface AiChatService {

    /**
     * Sends {@code prompt} to the configured chat model and returns its
     * text response.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason (network, authentication, rate
     *                              limiting, malformed response, ...)
     */
    String chat(String prompt);

    /**
     * Sends {@code systemPrompt} and {@code userMessage} as separate,
     * distinct messages (Spring AI's/the provider's own system/user role
     * separation — never concatenated into one string) and returns the text
     * response. Added in Phase 16 so a caller (the AI assistant) can supply
     * trusted system instructions separately from untrusted user/document
     * content — the actual technical control behind "retrieved document
     * content must never be treated as an instruction," not achievable
     * through {@link #chat(String)} alone.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Same as {@link #chat(String, String)}, additionally making {@code
     * tools} available for the model to invoke during this call (Phase 17,
     * CLAUDE.md §19). {@code tools} are plain, Spring-managed {@code @Tool}-
     * annotated beans (e.g. {@code ai.tools.CustomerTools}) — this method
     * never inspects, executes, or authorizes a tool call itself; Spring AI
     * resolves and invokes them, and each tool bean is independently
     * responsible for its own authorization ({@code @PreAuthorize}) and for
     * delegating to the existing business service layer. This method adds
     * no new tool-execution logic of its own.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason
     */
    String chat(String systemPrompt, String userMessage, List<Object> tools);
}
