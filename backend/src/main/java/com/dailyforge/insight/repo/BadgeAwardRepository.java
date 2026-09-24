package com.dailyforge.insight.repo;

import com.dailyforge.insight.domain.BadgeAward;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeAwardRepository extends JpaRepository<BadgeAward, UUID> {

    List<BadgeAward> findAllByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);

    Optional<BadgeAward> findByUserIdAndBadgeCodeAndPeriodStart(UUID userId, String badgeCode, LocalDate periodStart);

    List<BadgeAward> findAllByUserIdOrderByClaimedAtDesc(UUID userId);
}
