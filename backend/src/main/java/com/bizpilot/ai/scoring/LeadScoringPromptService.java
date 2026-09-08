package com.bizpilot.ai.scoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the versioned, fixed lead-scoring system prompt (Phase 18, project
 * instructions §17) from {@code
 * backend/src/main/resources/prompts/lead-scoring-system-v1.txt}. Mirrors
 * {@code ai.chat.AssistantPromptService} exactly — loaded once at bean
 * construction, never re-read per request, never parameterized by user
 * input or lead data. A separate class (not a reuse of {@code
 * AssistantPromptService}) because its content/purpose (a scoring
 * instruction) is unrelated to the chat assistant's persona/instructions;
 * sharing the class would couple two independently-versioned prompts. If
 * this prompt is ever revised, a new file ({@code
 * lead-scoring-system-v2.txt}) is the versioning mechanism, not mutating
 * this one (CLAUDE.md §39).
 */
@Component
public class LeadScoringPromptService {

    private final String systemPrompt;

    public LeadScoringPromptService(
            @Value("classpath:prompts/lead-scoring-system-v1.txt") Resource promptResource) {
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load the lead scoring system prompt resource", e);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
