package com.bizpilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code bizpilot.ai.*} (see application.yml), which in turn reads
 * {@code AI_ENABLED}/{@code AI_PROVIDER} from the environment — mirrors
 * {@code security.jwt.JwtProperties}'s exact shape and registration style.
 *
 * <p>Deliberately minimal: only the two things BizPilot's own code needs to
 * know about the AI subsystem at an application level — whether it's on at
 * all, and which provider is configured (for logging/diagnostics — never for
 * branching business logic on). Every provider-specific concern (which chat/
 * embedding model, temperature, API key, base URL, retry policy, ...) is
 * Spring AI's own {@code spring.ai.*} configuration's responsibility, not
 * duplicated here.
 *
 * <p>{@code enabled} defaults to {@code false} in application.yml — unlike
 * {@code JwtProperties.secret} (mandatory in every environment, since auth is
 * core to every phase already shipped), AI has no real feature depending on
 * it yet, so every environment without an explicit {@code AI_ENABLED=true}
 * and a real provider API key simply runs with the AI subsystem inert.
 */
@ConfigurationProperties(prefix = "bizpilot.ai")
public record AiProperties(
        boolean enabled,
        String provider
) {
}
