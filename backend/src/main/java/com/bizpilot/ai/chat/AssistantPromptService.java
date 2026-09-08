package com.bizpilot.ai.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the versioned, fixed system prompt (project instructions §9/§53)
 * from {@code backend/src/main/resources/prompts/assistant-system-v1.txt}
 * — the placeholder directory Phase 14 created for exactly this purpose.
 * Loaded once, at bean construction (singleton), not re-read per request.
 *
 * <p>Accepts no input — no user text, no document content, no request
 * data — and exposes only the fixed prompt string; nothing here ever
 * rewrites or parameterizes it. If the resource is ever revised, a new
 * file ({@code assistant-system-v2.txt}) is the versioning mechanism, not
 * mutating this one (CLAUDE.md §39: prompts must be versioned).
 */
@Component
public class AssistantPromptService {

    private final String systemPrompt;

    public AssistantPromptService(
            @Value("classpath:prompts/assistant-system-v1.txt") Resource promptResource) {
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load the assistant system prompt resource", e);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
