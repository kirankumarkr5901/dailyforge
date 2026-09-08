package com.dailyforge.identity.repo;

import com.dailyforge.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /** Housekeeping: expired tokens are dead weight, not history worth keeping. */
    void deleteByExpiresAtBefore(Instant cutoff);
}
