package com.dailyforge.reward.repo;

import com.dailyforge.reward.domain.RewardRedemption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    Optional<RewardRedemption> findByIdAndUserId(UUID id, UUID userId);

    List<RewardRedemption> findAllByUserIdOrderByRedeemedAtDesc(UUID userId);

    /**
     * How much of this period's allowance has been used.
     *
     * Counted from the redemptions themselves rather than from a stored tally, so the
     * allowance refreshes when the period turns without anything having to run — and a
     * refund gives the slot back for free, because a refunded row stops counting.
     */
    long countByRewardIdAndUserIdAndRefundedAtIsNullAndOccurredOnBetween(
            UUID rewardId, UUID userId, java.time.LocalDate from, java.time.LocalDate to);

    /** A one-off reward is redeemable again only once every non-refunded redemption has been refunded. */
    boolean existsByRewardIdAndUserIdAndRefundedAtIsNull(UUID rewardId, UUID userId);
}
