package com.dailyforge.reward.repo;

import com.dailyforge.reward.domain.RewardRedemption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    Optional<RewardRedemption> findByIdAndUserId(UUID id, UUID userId);

    List<RewardRedemption> findAllByUserIdOrderByRedeemedAtDesc(UUID userId);

    /** A one-off reward is redeemable again only once every non-refunded redemption has been refunded. */
    boolean existsByRewardIdAndUserIdAndRefundedAtIsNull(UUID rewardId, UUID userId);
}
