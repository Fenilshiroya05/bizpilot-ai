package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Test
    void embedBatchReturnsVectorsInOrderForASingleSubBatch() {
        List<String> texts = List.of("chunk one", "chunk two", "chunk three");
        List<float[]> vectors = List.of(new float[]{0.1f}, new float[]{0.2f}, new float[]{0.3f});
        when(embeddingModel.embed(texts)).thenReturn(vectors);

        List<float[]> result = service().embedBatch(texts);

        assertThat(result).isEqualTo(vectors);
    }

    @Test
    void embedBatchSplitsMoreThanOneHundredTextsIntoMultipleSubBatchCalls() {
        List<String> texts = java.util.stream.IntStream.range(0, 150)
                .mapToObj(i -> "chunk " + i)
                .toList();
        when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            List<String> input = invocation.getArgument(0);
            return input.stream().map(t -> new float[]{1f}).toList();
        });

        List<float[]> result = service().embedBatch(texts);

        assertThat(result).hasSize(150);
        verify(embeddingModel, times(2)).embed(anyList());
        verify(embeddingModel).embed(eq(texts.subList(0, 100)));
        verify(embeddingModel).embed(eq(texts.subList(100, 150)));
    }

    @Test
    void embedBatchReturnsEmptyListWithoutCallingTheProviderForAnEmptyInput() {
        List<float[]> result = service().embedBatch(List.of());

        assertThat(result).isEmpty();
        verify(embeddingModel, never()).embed(anyList());
    }

    @Test
    void embedBatchWrapsAProviderFailureInAiProviderException() {
        when(embeddingModel.embed(anyList())).thenThrow(new RuntimeException("rate limited"));

        assertThatThrownBy(() -> service().embedBatch(List.of("a", "b")))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void embedBatchAiProviderExceptionNeverLeaksTheRawProviderExceptionMessage() {
        when(embeddingModel.embed(anyList()))
                .thenThrow(new RuntimeException("api-key sk-super-secret-value rejected"));

        assertThatThrownBy(() -> service().embedBatch(List.of("a", "b")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("sk-super-secret-value"));
    }
}
