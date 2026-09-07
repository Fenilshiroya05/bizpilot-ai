package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Default {@link AiEmbeddingService} implementation, built directly on
 * Spring AI's {@link EmbeddingModel} — no provider-specific code, mirroring
 * {@link DefaultAiChatService}'s exact rationale and enablement mechanism
 * (only created when {@code bizpilot.ai.enabled=true}).
 */
@Service
@ConditionalOnProperty(prefix = "bizpilot.ai", name = "enabled", havingValue = "true")
public class DefaultAiEmbeddingService implements AiEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;

    public DefaultAiEmbeddingService(EmbeddingModel embeddingModel, AiProperties aiProperties) {
        this.embeddingModel = embeddingModel;
        this.aiProperties = aiProperties;
    }

    @Override
    public float[] embed(String text) {
        Instant start = Instant.now();
        try {
            float[] vector = embeddingModel.embed(text);
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            // Never logs the input text or the vector itself — only its
            // dimensionality, which is harmless metadata (project instructions §12).
            log.info("AI embedding request succeeded [provider={}, dimensions={}, durationMs={}]",
                    aiProperties.provider(), vector.length, durationMs);
            return vector;
        } catch (RuntimeException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("AI embedding request failed [provider={}, durationMs={}, errorType={}]",
                    aiProperties.provider(), durationMs, e.getClass().getSimpleName(), e);
            throw new AiProviderException("AI embedding request failed", e);
        }
    }
}
