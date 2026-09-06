package com.dailyforge.points.repo;

import com.dailyforge.points.domain.PointsRuleConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsRuleConfigRepository extends JpaRepository<PointsRuleConfig, UUID> {

    Optional<PointsRuleConfig> findByUserIdAndRuleCode(UUID userId, String ruleCode);

    Optional<PointsRuleConfig> findByUserIdIsNullAndRuleCode(String ruleCode);

    List<PointsRuleConfig> findAllByUserIdIsNull();
}
