package com.bizpilot.ai.scoring.dto;

/**
 * The raw, untrusted shape the model is asked to return (Phase 18, project
 * instructions §11) — the direct target type for {@code
 * AiChatService.chatForStructuredOutput}. Deliberately NOT the client-facing
 * response type: {@code priority} is a plain {@link String} here, not
 * {@code LeadPriority}, specifically so a model returning an unrecognized
 * value (e.g. {@code "URGENT"}, a value that exists in {@code
 * tasks.entity.TaskPriority} but not {@code sales.entity.LeadPriority}) is
 * deserialized successfully and then explicitly rejected by {@code
 * ai.scoring.LeadScoringService}'s own validation — never silently coerced,
 * and never allowed to fail with an opaque Jackson enum-binding exception
 * instead of a clear, narrow {@code LeadScoringValidationException}.
 *
 * <p>Every field is nullable/unvalidated at this layer on purpose — a
 * missing JSON property deserializes to {@code null} rather than throwing,
 * so {@code LeadScoringService} must (and does) treat every field as
 * untrusted input requiring its own explicit check.
 */
public record LeadScoreAiOutput(
        Integer score,
        String priority,
        String reasoning,
        String recommendedAction
) {
}
