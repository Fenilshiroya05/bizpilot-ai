package com.bizpilot.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code bizpilot.jwt.*} (see application.yml), which in turn reads
 * {@code JWT_SECRET} / {@code JWT_ACCESS_TOKEN_EXPIRATION_MINUTES} /
 * {@code JWT_REFRESH_TOKEN_EXPIRATION_DAYS} from the environment. {@code secret}
 * has no default and must be supplied — the app fails fast at startup otherwise.
 */
@ConfigurationProperties(prefix = "bizpilot.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMinutes,
        long refreshTokenExpirationDays
) {
}
