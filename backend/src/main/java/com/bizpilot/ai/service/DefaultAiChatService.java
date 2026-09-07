package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Default {@link AiChatService} implementation, built directly on Spring
 * AI's {@link ChatClient} — the fluent, higher-level API CLAUDE.md §5
 * explicitly names ("Use Spring AI for: ChatClient..."). No provider-specific
 * code appears anywhere in this class: {@code ChatClient.Builder} is
 * auto-configured by whichever {@code spring-ai-starter-model-*} is on the
 * classpath and whatever {@code spring.ai.model.chat} selects — this class
 * never branches on a provider name.
 *
 * <p>Only created when {@code bizpilot.ai.enabled=true} — the single
 * {@code @ConditionalOnProperty} below is the entire "enablement mechanism"
 * (project instructions §6 explicitly forbid a "complicated custom
 * auto-configuration framework"). When disabled, this bean simply doesn't
 * exist; Spring AI's own {@code ChatClient.Builder} bean may still exist in
 * the context (it's gated by Spring AI's own {@code spring.ai.model.chat}
 * property, not this one), but nothing in the application ever calls it,
 * so no network request to the provider ever occurs and no API key is ever
 * required.
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DefaultAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChatService.class);

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    public DefaultAiChatService(ChatClient.Builder chatClientBuilder, AiProperties aiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.aiProperties = aiProperties;
    }

    @Override
    public String chat(String prompt) {
        Instant start = Instant.now();
        try {
            ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
            logOutcome(true, response, start, null);
            return response.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            logOutcome(false, null, start, e);
            throw new AiProviderException("AI chat request failed", e);
        }
    }

    /**
     * Logs only safe metadata (project instructions §12) — provider, model
     * (read back from the response itself, never a provider-specific config
     * property, so this stays provider-agnostic), duration, success/failure,
     * and token counts where the provider reports them. Never the prompt,
     * never the response text.
     */
    private void logOutcome(boolean success, ChatResponse response, Instant start, Exception error) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        String model = response != null ? response.getMetadata().getModel() : "unknown";
        Usage usage = response != null ? response.getMetadata().getUsage() : null;
        if (success) {
            log.info("AI chat request succeeded [provider={}, model={}, durationMs={}, promptTokens={}, completionTokens={}]",
                    aiProperties.provider(), model, durationMs,
                    usage != null ? usage.getPromptTokens() : null,
                    usage != null ? usage.getCompletionTokens() : null);
        } else {
            log.error("AI chat request failed [provider={}, durationMs={}, errorType={}]",
                    aiProperties.provider(), durationMs, error.getClass().getSimpleName(), error);
        }
    }
}
