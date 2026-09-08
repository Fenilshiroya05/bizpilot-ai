package com.bizpilot.ai.chat;

import com.bizpilot.ai.service.AiChatService;

import java.util.ArrayList;
import java.util.List;

/**
 * A zero-network {@link AiChatService} test double (project instructions
 * §64/§65) that records every {@code (systemPrompt, userMessage)} pair it's
 * called with — the exact capability the prompt-injection integration test
 * needs (verifying the two are kept genuinely separate, not just that "the
 * response looks okay").
 */
public class RecordingFakeAiChatService implements AiChatService {

    public record Invocation(String systemPrompt, String userMessage) {
    }

    private final List<Invocation> invocations = new ArrayList<>();
    private String cannedAnswer = "This is a deterministic test answer.";

    @Override
    public String chat(String prompt) {
        invocations.add(new Invocation(null, prompt));
        return cannedAnswer;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        invocations.add(new Invocation(systemPrompt, userMessage));
        return cannedAnswer;
    }

    public List<Invocation> invocations() {
        return invocations;
    }

    public void reset() {
        invocations.clear();
    }

    public void setCannedAnswer(String cannedAnswer) {
        this.cannedAnswer = cannedAnswer;
    }
}
