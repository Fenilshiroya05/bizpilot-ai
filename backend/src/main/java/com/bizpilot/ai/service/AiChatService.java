package com.bizpilot.ai.service;

import com.bizpilot.ai.exception.AiProviderException;

/**
 * The only surface future BizPilot business code should depend on for AI
 * chat capability (CLAUDE.md §5) — never {@code org.springframework.ai.*}
 * types directly, and never a specific provider's SDK. Spring AI's own
 * auto-configuration (selected via {@code spring.ai.model.chat}) is what
 * makes the underlying provider swappable; this interface exists purely to
 * keep that Spring AI dependency out of business modules, not to reimplement
 * provider selection.
 *
 * <p>Deliberately minimal (project instructions §7): a single prompt in, a
 * single text response out. No conversation history, no memory, no tool/
 * function calling, no streaming, no RAG — those are later phases' concerns
 * and will very likely need a richer method/request shape once their actual
 * requirements are known; inventing that shape now would be exactly the kind
 * of speculative domain model this phase must avoid.
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
}
