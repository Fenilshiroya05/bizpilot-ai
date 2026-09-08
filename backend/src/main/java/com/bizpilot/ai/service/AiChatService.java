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
 * separation; Phase 17 added tool calling ({@link #chat(String, String, List)});
 * Phase 18 adds structured (non-prose) output ({@link #chatForStructuredOutput}) —
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

    /**
     * Sends {@code systemPrompt} and {@code userMessage} exactly as
     * {@link #chat(String, String)} does, but asks the model to produce
     * output matching {@code responseType}'s shape instead of prose (Phase
     * 18, CLAUDE.md §12) — Spring AI's structured-output support ({@code
     * ChatClient.CallResponseSpec.entity(Class)}, backed by {@code
     * BeanOutputConverter}), verified against the actual Spring AI 1.1.8
     * API/bytecode: {@code entity(Class)} automatically attaches the target
     * type's JSON schema to the request as a format instruction, so callers
     * do not need to embed schema wording in {@code systemPrompt}/{@code
     * userMessage} themselves.
     *
     * <p>This method performs no semantic/business validation of the
     * returned object's field values — it only guarantees the response was
     * successfully parsed into {@code responseType}'s shape. Validating
     * that the values themselves make sense (range, enum membership,
     * length, ...) is entirely the caller's responsibility (see {@code
     * ai.scoring.LeadScoringService}). A response that cannot even be
     * parsed into {@code responseType} (malformed JSON, a value that can't
     * be coerced into a declared field's type) is treated exactly like any
     * other provider failure, per this interface's existing "malformed
     * response" contract.
     *
     * @throws AiProviderException if the underlying provider call fails for
     *                              any reason, including a response that
     *                              cannot be parsed into {@code responseType}
     */
    <T> T chatForStructuredOutput(String systemPrompt, String userMessage, Class<T> responseType);
}
