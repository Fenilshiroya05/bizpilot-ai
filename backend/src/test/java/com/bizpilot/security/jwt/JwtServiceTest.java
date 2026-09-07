package com.bizpilot.security.jwt;

import com.bizpilot.identity.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-1234567890";

    private JwtService serviceWith(long accessMinutes) {
        return new JwtService(new JwtProperties(SECRET, accessMinutes, 7));
    }

    @Test
    void generatesTokenThatParsesBackToTheSameUserRoleAndOrganization() {
        JwtService jwtService = serviceWith(15);
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, UserRole.ADMIN, organizationId);
        Optional<Jws<Claims>> parsed = jwtService.parseAndValidate(token);

        assertThat(parsed).isPresent();
        assertThat(jwtService.extractUserId(parsed.get())).isEqualTo(userId);
        assertThat(jwtService.extractRole(parsed.get())).isEqualTo(UserRole.ADMIN);
        assertThat(jwtService.extractOrganizationId(parsed.get())).isEqualTo(organizationId);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwtService = serviceWith(-1); // already expired the instant it's issued
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UserRole.EMPLOYEE, UUID.randomUUID());

        assertThat(jwtService.parseAndValidate(token)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        JwtService jwtService = serviceWith(15);

        assertThat(jwtService.parseAndValidate("this.is.not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService issuer = new JwtService(new JwtProperties("issuer-secret-1234567890-1234567890abcd", 15, 7));
        JwtService verifier = new JwtService(new JwtProperties("verifier-secret-1234567890-1234567890abc", 15, 7));

        String token = issuer.generateAccessToken(UUID.randomUUID(), UserRole.OWNER, UUID.randomUUID());

        assertThat(verifier.parseAndValidate(token)).isEmpty();
    }

    @Test
    void rejectsBlankToken() {
        JwtService jwtService = serviceWith(15);

        assertThat(jwtService.parseAndValidate("")).isEmpty();
    }

    @Test
    void constructorRejectsSecretShorterThan256Bits() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("too-short", 15, 7)));
    }
}
