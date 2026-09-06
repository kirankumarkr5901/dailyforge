package com.dailyforge.points.repo;

import com.dailyforge.points.domain.UserScoreCache;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserScoreCacheRepository extends JpaRepository<UserScoreCache, UUID> {

    /**
     * Takes a row lock for the duration of the enclosing transaction. Every award and
     * reversal reads the cache this way, so two requests racing to update the same
     * user's total serialise instead of one silently clobbering the other's increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserScoreCache c where c.userId = :userId")
    Optional<UserScoreCache> lockForUpdate(@Param("userId") UUID userId);
}
