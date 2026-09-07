package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link DefaultAiEmbeddingService} entirely against a
 * Mockito-mocked {@link EmbeddingModel} — no Spring context, no real OpenAI
 * call.
 */
class DefaultAiEmbeddingServiceTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final AiProperties aiProperties = new AiProperties(true, "openai");

    private DefaultAiEmbeddingService service() {
        return new DefaultAiEmbeddingService(embeddingModel, aiProperties);
    }

    @Test
    void embedReturnsTheVectorFromTheEmbeddingModel() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("hello world")).thenReturn(vector);

        float[] result = service().embed("hello world");

        assertThat(result).isEqualTo(vector);
    }

    @Test
    void embedWrapsAProviderFailureInAiProviderException() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("rate limited"));

        assertThatThrownBy(() -> service().embed("hello world"))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void aiProviderExceptionNeverLeaksTheRawProviderExceptionMessage() {
        when(embeddingModel.embed(anyString()))
                .thenThrow(new RuntimeException("api-key sk-super-secret-value rejected"));

        assertThatThrownBy(() -> service().embed("hello world"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("sk-super-secret-value"));
    }
}
