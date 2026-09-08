package com.bizpilot.ai.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bounds the OpenAI chat/embedding client's HTTP timeout to 30 seconds
 * (project instructions §31/§41) — otherwise entirely unconfigured today
 * (verified: no {@code spring.ai.openai.*} timeout property exists, and
 * Spring AI's own {@code OpenAiChatAutoConfiguration.openAiApi(...)} takes
 * an {@code ObjectProvider<RestClient.Builder>}, so supplying this bean is
 * the smallest, officially-supported customization point — not an invented
 * property). {@code spring.ai.openai.chat.timeout} and similar do not exist
 * in 1.1.8; verified directly against {@code OpenAiConnectionProperties}/
 * {@code OpenAiChatAutoConfiguration}'s actual source before writing this.
 *
 * <p>Only the synchronous (non-streaming) path is covered — Phase 16 never
 * calls {@code ChatClient.stream()} (streaming is explicitly out of scope),
 * so a {@code WebClient.Builder}/reactive timeout is not configured here;
 * add one only if a future phase introduces streaming.
 *
 * <p>Not gated by {@code bizpilot.ai.enabled} — a plain {@code
 * RestClient.Builder} bean is inert until something actually issues a
 * request through it, and Spring AI's own OpenAI beans (the only current
 * consumer) are themselves already gated by {@code spring.ai.model.*}.
 */
@Configuration
public class AiHttpClientConfig {

    private static final Duration OPENAI_HTTP_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(OPENAI_HTTP_TIMEOUT)
                .withReadTimeout(OPENAI_HTTP_TIMEOUT);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
