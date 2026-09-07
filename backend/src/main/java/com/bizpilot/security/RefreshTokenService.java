package com.bizpilot.security;

import com.bizpilot.identity.entity.User;
import com.bizpilot.security.entity.RefreshToken;
import com.bizpilot.security.exception.InvalidRefreshTokenException;
import com.bizpilot.security.jwt.JwtService;
import com.bizpilot.security.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens are opaque, cryptographically random values. Only their
 * SHA-256 hash is ever persisted; the raw value is returned to the client
 * exactly once (at issue/rotation time) and never stored or logged.
 *
 * <p>Rotation: every successful refresh revokes the presented token and
 * issues a new one. Presenting an already-revoked token is treated as a
 * signal of possible token theft and revokes every active refresh token for
 * that user, forcing re-authentication everywhere.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(jwtService.getRefreshTokenTtl());
        repository.save(new RefreshToken(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    @Transactional
    public RotatedToken rotate(String presentedRawToken) {
        RefreshToken existing = repository.findByTokenHash(hash(presentedRawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked()) {
            // Reuse of a token that was already rotated/revoked: treat as compromised
            // and kill every active session for this user. Runs in its own, separately
            // committed transaction (REQUIRES_NEW) so this security response survives
            // the rollback that the InvalidRefreshTokenException below triggers for the
            // *current* transaction — otherwise the revocation itself would be undone.
            UUID userId = existing.getUser().getId();
            requiresNewTransactionTemplate.executeWithoutResult(
                    status -> repository.revokeAllActiveForUser(userId, Instant.now()));
            throw new InvalidRefreshTokenException();
        }
        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        User user = existing.getUser();
        String newRawToken = generateRawToken();
        String newHash = hash(newRawToken);
        existing.revoke(newHash);

        Instant expiresAt = Instant.now().plus(jwtService.getRefreshTokenTtl());
        repository.save(new RefreshToken(user, newHash, expiresAt));

        return new RotatedToken(newRawToken, user);
    }

    @Transactional
    public void revoke(String presentedRawToken) {
        repository.findByTokenHash(hash(presentedRawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(null));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotatedToken(String rawToken, User user) {
    }
}
