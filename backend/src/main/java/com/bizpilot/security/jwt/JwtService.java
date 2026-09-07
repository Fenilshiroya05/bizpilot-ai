package com.bizpilot.security.jwt;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates access-token JWTs. Refresh tokens are opaque, random,
 * server-side-stored values (see {@code security.entity.RefreshToken}) — not
 * JWTs — so they can be revoked without a blocklist.
 *
 * <p>RBAC (Phase 6): the access token carries the user's fully-resolved
 * Spring Security authorities (both {@code ROLE_<name>} and permission-name
 * authorities, e.g. {@code CUSTOMER_READ}) as a single {@code authorities}
 * claim, computed once at login/refresh time from the authoritative
 * {@code roles}/{@code role_permissions} model — see {@code AuthService}.
 * This keeps the existing "no database lookup per request" design: the
 * filter never needs to re-resolve permissions from the DB to authorize a
 * request, only to mint a new token.
 */
@Component
public class JwtService {

    private static final String ISSUER = "bizpilot-ai";
    private static final String CLAIM_ORGANIZATION_ID = "orgId";
    private static final String CLAIM_AUTHORITIES = "authorities";
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

    public String generateAccessToken(UUID userId, UUID organizationId, Set<String> authorities) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ORGANIZATION_ID, organizationId.toString())
                .claim(CLAIM_AUTHORITIES, List.copyOf(authorities))
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

    public UUID extractOrganizationId(Jws<Claims> claims) {
        return UUID.fromString(claims.getPayload().get(CLAIM_ORGANIZATION_ID, String.class));
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractAuthorities(Jws<Claims> claims) {
        List<String> raw = claims.getPayload().get(CLAIM_AUTHORITIES, List.class);
        return raw.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
