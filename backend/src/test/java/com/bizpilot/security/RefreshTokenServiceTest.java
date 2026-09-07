package com.bizpilot.security;

import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.security.entity.RefreshToken;
import com.bizpilot.security.exception.AccountNotActiveException;
import com.bizpilot.security.exception.InvalidRefreshTokenException;
import com.bizpilot.security.jwt.JwtProperties;
import com.bizpilot.security.jwt.JwtService;
import com.bizpilot.security.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("test-secret-key-for-jwt-signing-1234567890", 15, 7));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        refreshTokenService = new RefreshTokenService(repository, jwtService, transactionManager);
        user = mock(User.class);
    }

    @Test
    void issueSavesAHashedTokenAndReturnsTheRawValue() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.issue(user);

        assertThat(rawToken).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(captor.getValue().getTokenHash()).hasSize(64); // hex-encoded SHA-256
    }

    @Test
    void rotateClaimsTheExistingTokenAndIssuesAFreshOne() {
        lenient().when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        RefreshToken existing = new RefreshToken(user, "existing-hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(repository.revokeIfActive(any(), anyString(), any(Instant.class))).thenReturn(1);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate("presented-raw-token");

        assertThat(rotated.rawToken()).isNotBlank();
        assertThat(rotated.user()).isEqualTo(user);
        verify(repository).revokeIfActive(eq(existing.getId()), anyString(), any(Instant.class));
        verify(repository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshToken expired = new RefreshToken(user, "expired-hash", Instant.now().minus(1, ChronoUnit.SECONDS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("some-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsAnUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsATokenBelongingToANonActiveAccount() {
        when(user.getStatus()).thenReturn(UserStatus.DISABLED);
        RefreshToken existing = new RefreshToken(user, "existing-hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.rotate("presented-raw-token"))
                .isInstanceOf(AccountNotActiveException.class);

        verify(repository, never()).revokeIfActive(any(), anyString(), any());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesEveryActiveSessionForThatUser() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        lenient().when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        RefreshToken alreadyRevoked = new RefreshToken(user, "old-hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));
        // 0 = the atomic claim (UPDATE ... WHERE revoked_at IS NULL) matched no row:
        // already rotated/revoked, or a concurrent request won the race.
        when(repository.revokeIfActive(any(), anyString(), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-old-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllActiveForUser(eq(userId), any(Instant.class));
    }

    @Test
    void revokeMarksAnActiveTokenOwnedByTheCallerAsRevokedWithNoReplacement() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        RefreshToken active = new RefreshToken(user, "active-hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(active));

        refreshTokenService.revoke("some-token", userId);

        assertThat(active.isRevoked()).isTrue();
        assertThat(active.getReplacedByTokenHash()).isNull();
    }

    @Test
    void revokeDoesNothingIfTheTokenBelongsToADifferentUser() {
        when(user.getId()).thenReturn(UUID.randomUUID());
        RefreshToken active = new RefreshToken(user, "active-hash", Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(active));

        refreshTokenService.revoke("some-token", UUID.randomUUID());

        assertThat(active.isRevoked()).isFalse();
    }
}
