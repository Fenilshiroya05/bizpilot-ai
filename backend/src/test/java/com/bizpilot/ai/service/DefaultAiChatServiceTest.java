package com.bizpilot.ai.service;

import com.bizpilot.ai.config.AiProperties;
import com.bizpilot.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link DefaultAiChatService} entirely against a Mockito-mocked
 * {@link ChatClient} — no Spring context, no real OpenAI call, mirroring
 * every other {@code *ServiceTest} in this codebase (e.g.
 * {@code sales.service.QuotationServiceTest}).
 */
class DefaultAiChatServiceTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    private final AiProperties aiProperties = new AiProperties(true, "openai");

    private DefaultAiChatService service() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        return new DefaultAiChatService(chatClientBuilder, aiProperties);
    }

    private void stubSuccessfulResponse(String responseText) {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(responseText);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getModel()).thenReturn("gpt-4o-mini");
        when(metadata.getUsage()).thenReturn(null);
    }

    @Test
    void chatReturnsTheAssistantResponseText() {
        stubSuccessfulResponse("Hello from the assistant");

        String result = service().chat("What is our revenue?");

        assertThat(result).isEqualTo("Hello from the assistant");
    }

    @Test
    void chatSendsThePromptAsTheUserMessage() {
        stubSuccessfulResponse("ok");

        service().chat("What is our revenue?");

        verify(requestSpec).user("What is our revenue?");
    }

    @Test
    void chatWrapsAProviderFailureInAiProviderException() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("connection reset"));

        assertThatThrownBy(() -> service().chat("prompt"))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void aiProviderExceptionNeverLeaksTheRawProviderExceptionMessage() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("api-key sk-super-secret-value rejected"));

        assertThatThrownBy(() -> service().chat("prompt"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("sk-super-secret-value"));
    }
}
