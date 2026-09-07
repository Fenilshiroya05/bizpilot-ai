package com.bizpilot.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AiProperties} binds correctly from {@code bizpilot.ai.*}
 * without requiring a full {@code @SpringBootTest} context —
 * {@link ApplicationContextRunner} is Spring Boot's own lightweight tool
 * purpose-built for testing {@code @ConfigurationProperties}/conditional-bean
 * behavior in isolation, avoiding the "complicated custom auto-configuration
 * framework" this phase must not introduce.
 */
class AiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class);

    @Test
    void bindsDisabledWhenExplicitlyConfiguredFalse() {
        // Mirrors the real application.yml default (${AI_ENABLED:false}) —
        // the property is always present with a resolved value in every
        // real environment, never truly absent.
        contextRunner
                .withPropertyValues("bizpilot.ai.enabled=false", "bizpilot.ai.provider=openai")
                .run(context -> {
                    AiProperties properties = context.getBean(AiProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.provider()).isEqualTo("openai");
                });
    }

    @Test
    void bindsEnabledAndProviderWhenConfigured() {
        contextRunner
                .withPropertyValues("bizpilot.ai.enabled=true", "bizpilot.ai.provider=openai")
                .run(context -> {
                    AiProperties properties = context.getBean(AiProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.provider()).isEqualTo("openai");
                });
    }
}
