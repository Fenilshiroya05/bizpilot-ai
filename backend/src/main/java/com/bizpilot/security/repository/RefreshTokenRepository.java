package com.bizpilot.security.repository;

import com.bizpilot.security.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.user.id = :userId and rt.revokedAt is null")
    void revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Atomically claims a token for rotation: only succeeds (returns 1) if the
     * token is still active at the moment of the update. Using a conditional
     * {@code WHERE revoked_at IS NULL} update (rather than read-then-write)
     * closes a TOCTOU race where two concurrent requests presenting the same
     * token could otherwise both read "not yet revoked" and both rotate it.
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now, rt.replacedByTokenHash = :newHash "
            + "where rt.id = :id and rt.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("newHash") String newHash, @Param("now") Instant now);
}
