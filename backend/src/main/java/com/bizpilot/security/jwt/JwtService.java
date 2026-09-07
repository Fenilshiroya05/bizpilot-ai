package com.bizpilot.security.jwt;

import com.bizpilot.identity.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates access-token JWTs. Refresh tokens are opaque, random,
 * server-side-stored values (see {@code security.entity.RefreshToken}) — not
 * JWTs — so they can be revoked without a blocklist.
 */
@Component
public class JwtService {

    private static final String ISSUER = "bizpilot-ai";
    private static final String CLAIM_ROLE = "role";
    private static final int MIN_SECRET_BYTES = 32; // 256 bits, required for HS256

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256 signing");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Duration getAccessTokenTtl() {
        return Duration.ofMinutes(properties.accessTokenExpirationMinutes());
    }

    public Duration getRefreshTokenTtl() {
        return Duration.ofDays(properties.refreshTokenExpirationDays());
    }

    /** Returns empty if the token is missing, malformed, expired, or has an invalid signature. */
    public Optional<Jws<Claims>> parseAndValidate(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public UUID extractUserId(Jws<Claims> claims) {
        return UUID.fromString(claims.getPayload().getSubject());
    }

    public UserRole extractRole(Jws<Claims> claims) {
        return UserRole.valueOf(claims.getPayload().get(CLAIM_ROLE, String.class));
    }
}
