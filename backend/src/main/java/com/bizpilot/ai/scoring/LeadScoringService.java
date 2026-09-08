package com.bizpilot.ai.scoring;

import com.bizpilot.ai.exception.AiDisabledException;
import com.bizpilot.ai.exception.LeadScoringValidationException;
import com.bizpilot.ai.scoring.dto.LeadScoreAiOutput;
import com.bizpilot.ai.scoring.dto.LeadScoreResponse;
import com.bizpilot.ai.service.AiChatService;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadActivity;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.service.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates AI lead scoring (Phase 18, CLAUDE.md §12): fetch one lead and
 * its bounded recent history through the existing {@link LeadService}
 * (never {@code LeadRepository} directly), build a deterministic scoring
 * context, call the AI for a structured assessment, validate that
 * assessment, and return it. Never mutates the lead — there is no code path
 * here that calls {@code LeadService.update}/any save method.
 *
 * <p><b>Always exists as a bean, regardless of {@code bizpilot.ai.enabled}</b> —
 * {@link AiChatService} is held via {@link ObjectProvider} and checked
 * <em>before</em> any lead/history fetch (project instructions §8), exactly
 * mirroring {@code ai.chat.DefaultAiAssistantService}'s established pattern
 * for its own AI-gated collaborators. This keeps the disabled path free of
 * unnecessary database work and requires no second AI on/off switch.
 *
 * <p><b>Read-only, advisory-only.</b> The flow is strictly READ (lead +
 * history via {@code LeadService}) → AI COMPUTE (structured output) →
 * VALIDATE → RETURN. The returned {@link LeadScoreResponse} is never
 * persisted and never written back to the {@link Lead} entity.
 */
@Service
public class LeadScoringService {

    private static final Logger log = LoggerFactory.getLogger(LeadScoringService.class);

    /** Locked (project instructions §5/§13): score must fall in [0, 100]. */
    static final int MIN_SCORE = 0;
    static final int MAX_SCORE = 100;

    /**
     * Fixed, reasonable bounds for the model's free-text fields (project
     * instructions §13) — generous enough for a genuine explanation/next
     * step, small enough that a runaway/adversarial response is rejected
     * rather than silently accepted.
     */
    static final int MAX_REASONING_LENGTH = 1000;
    static final int MAX_RECOMMENDED_ACTION_LENGTH = 500;

    private static final Sort RECENT_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Pageable RECENT_HISTORY_PAGE =
            PageRequest.of(0, LeadScoringContextBuilder.MAX_ACTIVITY_ITEMS, RECENT_FIRST);

    private final LeadService leadService;
    private final ObjectProvider<AiChatService> aiChatService;
    private final LeadScoringPromptService promptService;
    private final LeadScoringContextBuilder contextBuilder;

    public LeadScoringService(LeadService leadService, ObjectProvider<AiChatService> aiChatService,
                               LeadScoringPromptService promptService, LeadScoringContextBuilder contextBuilder) {
        this.leadService = leadService;
        this.aiChatService = aiChatService;
        this.promptService = promptService;
        this.contextBuilder = contextBuilder;
    }

    public LeadScoreResponse score(UUID leadId) {
        AiChatService chat = aiChatService.getIfAvailable();
        if (chat == null) {
            // Checked first, before any lead/history fetch — project
            // instructions §8, mirrors DefaultAiAssistantService.ask.
            throw new AiDisabledException();
        }

        Instant start = Instant.now();

        // Tenant-safe: throws LeadNotFoundException, indistinguishably,
        // for both "doesn't exist" and "belongs to another organization" —
        // unmodified LeadService behavior, never re-implemented here.
        Lead lead = leadService.getById(leadId);
        Page<LeadActivity> recentHistory = leadService.getHistory(leadId, RECENT_HISTORY_PAGE);

        String userMessage = contextBuilder.build(lead, recentHistory.getContent());
        LeadScoreAiOutput raw = chat.chatForStructuredOutput(
                promptService.systemPrompt(), userMessage, LeadScoreAiOutput.class);

        LeadScoreResponse response = validateAndMap(leadId, raw);

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("AI lead scoring request succeeded [leadId={}, durationMs={}]", leadId, durationMs);
        return response;
    }

    /**
     * AI output is untrusted (project instructions §13/§14): every field is
     * checked explicitly and rejected outright on failure — never clamped,
     * defaulted, or otherwise silently repaired into something merely
     * valid-looking.
     */
    private static LeadScoreResponse validateAndMap(UUID leadId, LeadScoreAiOutput raw) {
        if (raw == null) {
            throw new LeadScoringValidationException("AI structured output was null for lead " + leadId);
        }

        Integer score = raw.score();
        if (score == null) {
            throw new LeadScoringValidationException("AI output missing score for lead " + leadId);
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new LeadScoringValidationException(
                    "AI output score out of range [" + MIN_SCORE + "," + MAX_SCORE + "] for lead " + leadId
                            + ": " + score);
        }

        if (!StringUtils.hasText(raw.priority())) {
            throw new LeadScoringValidationException("AI output missing priority for lead " + leadId);
        }
        LeadPriority priority;
        try {
            priority = LeadPriority.valueOf(raw.priority().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LeadScoringValidationException(
                    "AI output priority is not a recognized LeadPriority for lead " + leadId + ": " + raw.priority());
        }

        String reasoning = raw.reasoning();
        if (!StringUtils.hasText(reasoning)) {
            throw new LeadScoringValidationException("AI output missing reasoning for lead " + leadId);
        }
        if (reasoning.length() > MAX_REASONING_LENGTH) {
            throw new LeadScoringValidationException(
                    "AI output reasoning exceeds the maximum length of " + MAX_REASONING_LENGTH
                            + " characters for lead " + leadId);
        }

        String recommendedAction = raw.recommendedAction();
        if (!StringUtils.hasText(recommendedAction)) {
            throw new LeadScoringValidationException("AI output missing recommendedAction for lead " + leadId);
        }
        if (recommendedAction.length() > MAX_RECOMMENDED_ACTION_LENGTH) {
            throw new LeadScoringValidationException(
                    "AI output recommendedAction exceeds the maximum length of " + MAX_RECOMMENDED_ACTION_LENGTH
                            + " characters for lead " + leadId);
        }

        return new LeadScoreResponse(leadId, score, priority, reasoning.trim(), recommendedAction.trim(),
                Instant.now());
    }
}
