package com.bizpilot.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AiProperties} — mirrors {@code security.SecurityConfig}'s
 * {@code @EnableConfigurationProperties(JwtProperties.class)} role, scoped to
 * the {@code ai} module instead of piggybacking on an unrelated module's
 * configuration class.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
}
