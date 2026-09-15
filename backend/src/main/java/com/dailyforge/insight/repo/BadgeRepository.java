package com.dailyforge.insight.repo;

import com.dailyforge.insight.domain.Badge;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The badge catalogue. Seeded by migration, never written to at runtime. */
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findAllByPeriodAndActiveTrueOrderBySortOrderAsc(RecapPeriod period);

    Optional<Badge> findByCodeAndActiveTrue(String code);
}
