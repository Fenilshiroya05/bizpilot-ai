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
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Internal sub-batch size for {@link #embedBatch(List)} (project
     * instructions §29: "a reasonable initial batch size is 100 texts...
     * keep this internal") — deliberately not a configuration property.
     */
    private static final int MAX_BATCH_SIZE = 100;

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

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        Instant start = Instant.now();
        try {
            List<float[]> results = new ArrayList<>(texts.size());
            int batchCount = 0;
            for (int fromIndex = 0; fromIndex < texts.size(); fromIndex += MAX_BATCH_SIZE) {
                int toIndex = Math.min(fromIndex + MAX_BATCH_SIZE, texts.size());
                // EmbeddingModel.embed(List<String>) is Spring AI's own
                // batched call (one provider request per sub-batch, not one
                // per text) — see EmbeddingModel's default method.
                results.addAll(embeddingModel.embed(texts.subList(fromIndex, toIndex)));
                batchCount++;
            }
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("AI batch embedding request succeeded [provider={}, textCount={}, batchCount={}, durationMs={}]",
                    aiProperties.provider(), texts.size(), batchCount, durationMs);
            return results;
        } catch (RuntimeException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("AI batch embedding request failed [provider={}, textCount={}, durationMs={}, errorType={}]",
                    aiProperties.provider(), texts.size(), durationMs, e.getClass().getSimpleName(), e);
            throw new AiProviderException("AI batch embedding request failed", e);
        }
    }
}
