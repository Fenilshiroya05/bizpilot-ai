package com.bizpilot.ai.chat.controller;

import com.bizpilot.ai.chat.AiAssistantService;
import com.bizpilot.ai.chat.dto.AiChatRequest;
import com.bizpilot.ai.chat.dto.AiChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Phase 16 AI assistant endpoint (CLAUDE.md §18, §28 — {@code /api/v1/ai}
 * is the reserved prefix). Deliberately thin: HTTP concerns only — request
 * validation, authorization, delegation, response. No retrieval, no prompt
 * construction, no provider call, and no tenant-filter logic live here;
 * {@link AiAssistantService} owns all of that (project instructions §13/§48).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('AI_USE')")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiAssistantService.ask(request.message());
    }
}
