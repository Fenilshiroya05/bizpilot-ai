package com.bizpilot.ai.exception;

/**
 * Thrown by {@code ai.chat.DefaultAiAssistantService} when {@code
 * bizpilot.ai.enabled=false} (or, equivalently, the AI-gated {@code
 * DocumentRetrievalService}/{@code AiChatService} beans don't exist) —
 * checked first, before any retrieval or provider call is attempted
 * (Phase 16, project instructions §27/§55). Mapped by {@code
 * GlobalExceptionHandler} to {@code 503}/{@code AI_DISABLED}.
 */
public class AiDisabledException extends RuntimeException {

    public AiDisabledException() {
        super("The AI assistant is currently disabled");
    }
}
