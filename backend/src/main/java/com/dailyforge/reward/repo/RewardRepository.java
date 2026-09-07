package com.dailyforge.reward.repo;

import com.dailyforge.reward.domain.Reward;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, UUID> {

    Optional<Reward> findByIdAndUserId(UUID id, UUID userId);

    List<Reward> findAllByUserIdAndArchivedAtIsNullOrderByCostAsc(UUID userId);
}
