package com.bizpilot.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Operational tuning for the Phase 15 document-processing pipeline
 * (executor sizing, recovery-sweep cadence and staleness thresholds) —
 * mirrors {@code ai.config.AiProperties}'s pattern. Deliberately does NOT
 * include the embedding sub-batch size (project instructions §29: "keep
 * this internal" — a hardcoded constant on {@code DefaultAiEmbeddingService}).
 *
 * <p>No Java-side defaults: every field is always supplied by
 * application.yml's own {@code ${ENV_VAR:default}} resolution (the same
 * convention {@code AiProperties} follows), so the effective default lives
 * in one place.
 */
@ConfigurationProperties(prefix = "bizpilot.documents.processing")
public record DocumentProcessingProperties(
        int executorCorePoolSize,
        int executorMaxPoolSize,
        int executorQueueCapacity,
        Duration recoveryInterval,
        Duration staleUploadedThreshold,
        Duration staleProcessingThreshold
) {
}
