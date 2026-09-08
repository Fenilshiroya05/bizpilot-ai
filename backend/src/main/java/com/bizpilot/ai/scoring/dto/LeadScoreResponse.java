package com.bizpilot.ai.scoring.dto;

import com.bizpilot.sales.entity.LeadPriority;

import java.time.Instant;
import java.util.UUID;

/**
 * The client-facing result of {@code POST /api/v1/leads/{id}/score} (Phase
 * 18, CLAUDE.md §12). Every field here has already passed {@code
 * ai.scoring.LeadScoringService}'s validation — this is never the model's
 * raw, unvalidated output (see {@link LeadScoreAiOutput}).
 *
 * <p><b>{@code priority} is an AI-generated suggestion, not the lead's
 * authoritative {@code Lead.priority}.</b> This response is never written
 * back to the {@code Lead} entity by any code path — it exists only to be
 * returned in this HTTP response (project instructions §1/§24). Deliberately
 * not named/shaped like {@code sales.dto.LeadResponse} so it can never be
 * mistaken for one at a glance.
 */
public record LeadScoreResponse(
        UUID leadId,
        int score,
        LeadPriority priority,
        String reasoning,
        String recommendedAction,
        Instant generatedAt
) {
}
