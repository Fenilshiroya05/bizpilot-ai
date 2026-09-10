package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * <p><b>{@link EmbeddingModel} is held via {@link ObjectProvider}</b>
 * (local-chat-only scope addition, symmetric with the identical fix on
 * {@code ai.retrieval.DefaultDocumentRetrievalService}) — this class's
 * constructor used to take {@link EmbeddingModel} as a plain, required
 * argument, which would fail application startup outright in a chat-only
 * environment with no embedding provider configured ({@code
 * AI_EMBEDDING_PROVIDER} left at its default {@code none}). An earlier
 * attempt gated this whole bean with {@code @ConditionalOnBean(EmbeddingModel.class)}
 * instead — that was INCORRECT and has been reverted, for the same reason
 * documented on {@code DefaultDocumentRetrievalService}: Spring Boot
 * evaluates {@code @ConditionalOnBean} against bean definitions already
 * registered at the time this plain, component-scanned {@code @Service} is
 * processed, which happens before the real provider's auto-configuration
 * registers its {@code EmbeddingModel} bean definition — the condition would
 * incorrectly evaluate as "absent" even in a fully, correctly configured
 * environment. Holding {@link EmbeddingModel} via {@link ObjectProvider}
 * instead sidesteps bean-creation-order entirely: this bean always exists
 * once {@code bizpilot.ai.enabled=true}; {@link #embed}/{@link #embedBatch}
 * resolve it lazily and throw a clear {@link AiProviderException} — the same
 * exception type/handling already used for every other provider failure —
 * only if an actual embedding is attempted with none configured (e.g. a
 * document upload in a chat-only environment; safely caught by {@code
 * documents.processing.DocumentProcessingService}'s existing catch-all,
 * unmodified, resulting in a {@code FAILED} document rather than a crash).
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

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final AiProperties aiProperties;

    public DefaultAiEmbeddingService(ObjectProvider<EmbeddingModel> embeddingModel, AiProperties aiProperties) {
        this.embeddingModel = embeddingModel;
        this.aiProperties = aiProperties;
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            throw new AiProviderException("No embedding model is configured in this environment", null);
        }
        return model;
    }

    @Override
    public float[] embed(String text) {
        Instant start = Instant.now();
        try {
            float[] vector = requireEmbeddingModel().embed(text);
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
            if (e instanceof AiProviderException aiProviderException) {
                throw aiProviderException;
            }
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
            EmbeddingModel model = requireEmbeddingModel();
            List<float[]> results = new ArrayList<>(texts.size());
            int batchCount = 0;
            for (int fromIndex = 0; fromIndex < texts.size(); fromIndex += MAX_BATCH_SIZE) {
                int toIndex = Math.min(fromIndex + MAX_BATCH_SIZE, texts.size());
                // EmbeddingModel.embed(List<String>) is Spring AI's own
                // batched call (one provider request per sub-batch, not one
                // per text) — see EmbeddingModel's default method.
                results.addAll(model.embed(texts.subList(fromIndex, toIndex)));
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
            if (e instanceof AiProviderException aiProviderException) {
                throw aiProviderException;
            }
            throw new AiProviderException("AI batch embedding request failed", e);
        }
    }
}
