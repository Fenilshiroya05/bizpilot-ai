package com.bizpilot.documents.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables the async/scheduling infrastructure Phase 15's processing
 * pipeline needs, and defines its dedicated, bounded executor — never the
 * common pool, and never an unbounded queue (project instructions §14).
 *
 * <p>Deliberately NOT gated behind {@code bizpilot.ai.enabled} — {@code
 * @EnableAsync}/{@code @EnableScheduling} and a small idle thread pool are
 * harmless framework wiring regardless of whether AI is enabled; only the
 * beans that actually submit work to this executor ({@code
 * DocumentProcessingService}, {@code DocumentRecoveryScheduler}) are
 * conditional, so the pool simply sits idle, unused, when AI is disabled
 * (project instructions §40: normal startup must never depend on Phase 15
 * being enabled).
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(DocumentProcessingProperties.class)
public class DocumentProcessingConfig {

    public static final String EXECUTOR_BEAN_NAME = "documentProcessingExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    public Executor documentProcessingExecutor(DocumentProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.executorCorePoolSize());
        executor.setMaxPoolSize(properties.executorMaxPoolSize());
        executor.setQueueCapacity(properties.executorQueueCapacity());
        executor.setThreadNamePrefix("doc-processing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
