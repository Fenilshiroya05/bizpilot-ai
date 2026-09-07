package com.bizpilot.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Supplies the two Spring AI infrastructure beans that only exist when a
 * real OpenAI provider is configured, so Phase 15 integration tests can run
 * with {@code bizpilot.ai.enabled=true} (needed for {@code
 * DocumentProcessingService}/{@code DocumentRetrievalService}/{@code
 * DefaultDocumentVectorStoreService} to even be created) without a real
 * {@code OPENAI_API_KEY} or any network call (project instructions §44).
 *
 * <p>{@link #embeddingModel()} is the one that actually matters for these
 * tests — it is both what {@code DefaultAiEmbeddingService} wraps AND what
 * Spring AI's own {@code PgVectorStoreAutoConfiguration} requires as a
 * plain, non-optional constructor argument to create a real {@code
 * PgVectorStore} bean backed by the Testcontainers Postgres. {@link
 * #chatClientBuilder()} exists only because {@code DefaultAiChatService}
 * (unrelated to RAG) is ALSO created whenever {@code bizpilot.ai.enabled=true}
 * and would otherwise fail to construct — it is never exercised by any
 * Phase 15 test.
 */
@TestConfiguration
public class FakeAiInfrastructureTestConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new DeterministicFakeEmbeddingModel();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return builder;
    }
}
