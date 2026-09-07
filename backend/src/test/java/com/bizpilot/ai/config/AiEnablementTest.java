package com.bizpilot.ai.config;

import com.bizpilot.ai.service.AiChatService;
import com.bizpilot.ai.service.AiEmbeddingService;
import com.bizpilot.ai.service.DefaultAiChatService;
import com.bizpilot.ai.service.DefaultAiEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves the entire "AI enablement mechanism" (project instructions §6) is
 * exactly the {@code @ConditionalOnProperty} on {@link DefaultAiChatService}/
 * {@link DefaultAiEmbeddingService} — no custom auto-configuration class,
 * no manual bean-registration logic. Uses stub {@code ChatClient.Builder}/
 * {@code EmbeddingModel} beans rather than real Spring AI auto-configuration,
 * so this test never requires {@code OPENAI_API_KEY} or any network access.
 */
class AiEnablementTest {

    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(AiConfiguration.class, DefaultAiChatService.class,
                        DefaultAiEmbeddingService.class)
                .withBean(ChatClient.Builder.class, () -> mock(ChatClient.Builder.class))
                .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));
    }

    @Test
    void aiServiceBeansAreAbsentWhenDisabled() {
        baseRunner()
                .withPropertyValues("bizpilot.ai.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AiChatService.class);
                    assertThat(context).doesNotHaveBean(AiEmbeddingService.class);
                });
    }

    @Test
    void aiServiceBeansArePresentWhenEnabled() {
        baseRunner()
                .withPropertyValues("bizpilot.ai.enabled=true", "bizpilot.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiChatService.class);
                    assertThat(context).hasSingleBean(AiEmbeddingService.class);
                });
    }
}
