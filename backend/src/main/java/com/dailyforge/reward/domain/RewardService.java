package com.dailyforge.reward.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.reward.repo.RewardRedemptionRepository;
import com.dailyforge.reward.repo.RewardRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spending points (spec §5.4, §8.9) — the sink the plan's own first line promises. A
 * redemption is a source-tracked award of a negative amount, exactly like any other
 * award, which is what lets {@link #refund} reverse it cleanly via the same
 * {@code reverseBySource} every other reversible action in this app uses.
 */
@Service
public class RewardService {

    private static final String SOURCE_TYPE = "REWARD_REDEMPTION";

    private final RewardRepository rewards;
    private final RewardRedemptionRepository redemptions;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public RewardService(
            RewardRepository rewards,
            RewardRedemptionRepository redemptions,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    @Transactional
    public Reward create(
            UUID userId, String name, int cost, String icon, RewardTier tier, boolean repeatable, Integer stock) {
        return rewards.save(Reward.create(userId, name, cost, icon, tier, repeatable, stock));
    }

    @Transactional
    public Reward update(
            UUID id, UUID userId, String name, int cost, String icon, RewardTier tier, boolean repeatable, Integer stock) {
        Reward reward = requireOwned(id, userId);
        if (reward.isArchived()) {
            throw ApiException.notFound("That reward");
        }
        reward.update(name, cost, icon, tier, repeatable, stock);
        return rewards.save(reward);
    }

    @Transactional(readOnly = true)
    public List<Reward> list(UUID userId) {
        return rewards.findAllByUserIdAndArchivedAtIsNullOrderByCostAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<RewardRedemption> listRedemptions(UUID userId) {
        return redemptions.findAllByUserIdOrderByRedeemedAtDesc(userId);
    }

    @Transactional
    public void archive(UUID id, UUID userId) {
        Reward reward = requireOwned(id, userId);
        reward.archive(Instant.now());
        rewards.save(reward);
    }

    public Reward requireOwned(UUID id, UUID userId) {
        return rewards.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That reward"));
    }

    public record Redemption(RewardRedemption redemption, PointsResult points) {}

    @Transactional
    public Redemption redeem(UUID rewardId, UUID userId) {
        Reward reward = requireOwned(rewardId, userId);
        if (reward.isArchived()) {
            throw ApiException.notFound("That reward");
        }
        if (reward.isOutOfStock()) {
            throw new ApiException(ErrorCode.OUT_OF_RANGE, HttpStatus.UNPROCESSABLE_CONTENT, "That reward is out of stock.");
        }
        if (!reward.isRepeatable() && redemptions.existsByRewardIdAndUserIdAndRefundedAtIsNull(rewardId, userId)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That reward has already been redeemed.");
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        int currentTotal = points.snapshot(userId, zone).total();
        if (currentTotal < reward.getCost()) {
            throw new ApiException(
                    ErrorCode.OUT_OF_RANGE,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Costs " + reward.getCost() + ". You have " + currentTotal + ".");
        }

        var config = ruleConfigs.getSystemDefault("REWARD_REDEEM");
        if (!config.enabled()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Rewards are turned off.");
        }

        RewardRedemption redemption = redemptions.save(RewardRedemption.create(reward.getId(), userId, today, reward.getCost()));

        PointsResult result =
                points.award(
                        new AwardCommand(
                                userId,
                                today,
                                PointsCategory.REWARD,
                                "REWARD_REDEEM",
                                -reward.getCost(),
                                SOURCE_TYPE,
                                redemption.getId(),
                                "Redeemed — " + reward.getName(),
                                "reward-redeem:" + redemption.getId()));

        reward.decrementStock();
        rewards.save(reward);

        return new Redemption(redemption, result);
    }

    /** "Undo within the same day refunds" (spec §8.9) — compared against occurredOn, never redeemedAt. */
    @Transactional
    public PointsResult refund(UUID redemptionId, UUID userId) {
        RewardRedemption redemption =
                redemptions.findByIdAndUserId(redemptionId, userId).orElseThrow(() -> ApiException.notFound("That redemption"));
        if (redemption.isRefunded()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That redemption was already refunded.");
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);
        if (!redemption.getOccurredOn().isEqual(today)) {
            throw new ApiException(ErrorCode.ENTRY_LOCKED, HttpStatus.FORBIDDEN, "This can only be undone on the day it happened.");
        }

        points.reverseBySource(SOURCE_TYPE, redemption.getId(), "Reward redemption refunded");
        redemption.refund(Instant.now());
        redemptions.save(redemption);

        rewards.findById(redemption.getRewardId()).ifPresent(reward -> {
            reward.incrementStock();
            rewards.save(reward);
        });

        int newTotal = points.snapshot(userId, zone).total();
        return new PointsResult(List.of(), redemption.getPointsSpent(), newTotal, List.of());
    }
}
