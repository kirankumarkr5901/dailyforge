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

    /**
     * A reward plus how much of this period's allowance is left.
     *
     * {@code remaining} is null when the reward has no limit at all. {@code refreshesOn}
     * is the day the allowance comes back — the day after this period ends.
     */
    public record RewardView(Reward reward, Integer remaining, LocalDate periodStart, LocalDate refreshesOn) {
        public boolean soldOutForNow() {
            return remaining != null && remaining <= 0;
        }
    }

    /**
     * The reward list, each with what is left of its allowance for the current period.
     *
     * Derived on read rather than reset on a schedule: a scheduled reset would miss its
     * window every time this free-tier host slept through it, which is exactly the
     * failure the daily rollover needed a watermark to avoid. Reading it from the
     * redemptions cannot drift, because there is nothing to drift from.
     */
    @Transactional(readOnly = true)
    public List<RewardView> listWithAllowance(UUID userId) {
        LocalDate today = dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return list(userId).stream().map(reward -> view(reward, userId, today)).toList();
    }

    private RewardView view(Reward reward, UUID userId, LocalDate today) {
        RewardTier tier = reward.getTier();
        LocalDate start = tier.periodStart(today);
        LocalDate end = tier.periodEnd(today);
        Integer remaining =
                reward.hasStockLimit()
                        ? Math.max(0, reward.getStock() - (int) usedThisPeriod(reward.getId(), userId, start, end))
                        : null;
        return new RewardView(reward, remaining, start, tier.nextRefresh(today));
    }

    private long usedThisPeriod(UUID rewardId, UUID userId, LocalDate start, LocalDate end) {
        return redemptions.countByRewardIdAndUserIdAndRefundedAtIsNullAndOccurredOnBetween(rewardId, userId, start, end);
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
        if (!reward.isRepeatable() && redemptions.existsByRewardIdAndUserIdAndRefundedAtIsNull(rewardId, userId)) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That reward has already been redeemed.");
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        // Against this period's allowance, not a counter that drains once. Out of stock
        // is now a temporary condition, so the message says when it lifts rather than
        // implying the reward is finished forever.
        RewardView view = view(reward, userId, today);
        if (view.soldOutForNow()) {
            throw new ApiException(
                    ErrorCode.OUT_OF_RANGE,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "You have used this " + reward.getTier().windowLabel() + ". It comes back on " + view.refreshesOn() + ".");
        }

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

        // Stock is not touched: it is the allowance, and what is left of it is counted
        // from the redemptions themselves.
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

        // The allowance restores itself: a refunded redemption stops counting against
        // the period the moment it is marked refunded.

        int newTotal = points.snapshot(userId, zone).total();
        return new PointsResult(List.of(), redemption.getPointsSpent(), newTotal, List.of());
    }
}
