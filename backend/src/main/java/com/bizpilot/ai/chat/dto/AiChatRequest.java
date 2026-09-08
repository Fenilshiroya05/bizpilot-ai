package com.bizpilot.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code message} is the user's free-text question — deliberately the only
 * field. There is no {@code context}/{@code sources}/{@code conversationId}
 * field: the backend owns retrieval and source generation entirely (project
 * instructions §58/§59); the client cannot supply arbitrary knowledge context
 * or influence sources, and Phase 16 is stateless (no conversation to
 * reference).
 */
public record AiChatRequest(
        @NotBlank @Size(max = 2000) String message
) {
}
