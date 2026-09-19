package com.dailyforge.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.reward.domain.Reward;
import com.dailyforge.reward.domain.RewardService;
import com.dailyforge.reward.domain.RewardTier;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stock is an allowance per period, and the tier sets the period (owner request).
 *
 * The tiers always said what they meant — MICRO is a daily treat — but stock was a
 * single pool that counted to zero once and stayed there, so a daily treat could be
 * taken three times ever. These tests pin that the allowance comes back, and comes back
 * on the right day for each tier.
 *
 * Nothing resets anything: the remaining count is derived from the redemptions inside
 * the current period. That is what makes it correct on a host that sleeps, and it is
 * why moving the clock is enough to prove it here — there is no job to run.
 */
@SpringBootTest
@Import(RewardClockTestConfig.class)
@ActiveProfiles("test")
class RewardStockRefreshTest {

    @Autowired private RewardService rewardService;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private RewardClockTestConfig.MutableClock clock;

    /** A Thursday, so the week boundary is a real move rather than a same-day no-op. */
    private static final LocalDate THURSDAY = LocalDate.of(2026, 3, 12);
    private static final LocalDate MONDAY_BEFORE = LocalDate.of(2026, 3, 9);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 3, 16);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private UUID richUser() {
        UUID user = TestUsers.create(users, settings);
        points.award(
                AwardCommand.withoutSource(
                        user, THURSDAY, PointsCategory.ADJUSTMENT, "TEST_GRANT", 100_000, "test grant", "grant:" + UUID.randomUUID()));
        return user;
    }

    private RewardService.RewardView viewOf(UUID user, UUID rewardId) {
        return rewardService.listWithAllowance(user).stream()
                .filter(v -> v.reward().getId().equals(rewardId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aDailyRewardComesBackTomorrow() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Coffee", 50, "coffee", RewardTier.MICRO, true, 1);

        rewardService.redeem(reward.getId(), user);
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();
        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user)).isInstanceOf(ApiException.class);

        setToday(THURSDAY.plusDays(1));

        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(1);
        rewardService.redeem(reward.getId(), user);
    }

    @Test
    void aWeeklyRewardDoesNotComeBackUntilMonday() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Takeaway", 200, "pizza", RewardTier.WEEKLY, true, 1);

        rewardService.redeem(reward.getId(), user);

        // Still the same week on Sunday — the allowance has not turned over.
        setToday(LocalDate.of(2026, 3, 15));
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();
        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user)).isInstanceOf(ApiException.class);

        setToday(NEXT_MONDAY);
        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(1);
        rewardService.redeem(reward.getId(), user);
    }

    @Test
    void aMonthlyRewardDoesNotComeBackUntilTheFirst() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "New book", 2000, "book", RewardTier.MONTHLY, true, 1);

        rewardService.redeem(reward.getId(), user);

        setToday(LocalDate.of(2026, 3, 31));
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();

        setToday(LocalDate.of(2026, 4, 1));
        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(1);
        rewardService.redeem(reward.getId(), user);
    }

    /** Each tier reports the day its allowance next comes back, for the UI to say so. */
    @Test
    void eachTierReportsWhenItRefreshes() {
        setToday(THURSDAY);
        UUID user = richUser();
        UUID micro = rewardService.create(user, "Coffee", 10, "coffee", RewardTier.MICRO, true, 2).getId();
        UUID weekly = rewardService.create(user, "Takeaway", 20, "pizza", RewardTier.WEEKLY, true, 2).getId();
        UUID monthly = rewardService.create(user, "Book", 30, "book", RewardTier.MONTHLY, true, 2).getId();

        assertThat(viewOf(user, micro).refreshesOn()).isEqualTo(THURSDAY.plusDays(1));
        assertThat(viewOf(user, weekly).periodStart()).isEqualTo(MONDAY_BEFORE);
        assertThat(viewOf(user, weekly).refreshesOn()).isEqualTo(NEXT_MONDAY);
        assertThat(viewOf(user, monthly).periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(viewOf(user, monthly).refreshesOn()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void anAllowanceOfSeveralIsSpentOneAtATime() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Snack", 10, "cookie", RewardTier.MICRO, true, 3);

        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(3);
        rewardService.redeem(reward.getId(), user);
        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(2);
        rewardService.redeem(reward.getId(), user);
        rewardService.redeem(reward.getId(), user);
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();
        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user)).isInstanceOf(ApiException.class);
    }

    /** No stock set means no limit, and no refresh date to report either. */
    @Test
    void anUnlimitedRewardStaysUnlimited() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Walk", 5, "footprints", RewardTier.MICRO, true, null);

        rewardService.redeem(reward.getId(), user);
        rewardService.redeem(reward.getId(), user);

        var view = viewOf(user, reward.getId());
        assertThat(view.remaining()).isNull();
        assertThat(view.soldOutForNow()).isFalse();
    }

    /**
     * A refund gives the slot back with no second write. That falls out of deriving the
     * count rather than storing it — a refunded redemption simply stops counting.
     */
    @Test
    void aRefundReturnsTheSlotToThisPeriodsAllowance() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Coffee", 50, "coffee", RewardTier.MICRO, true, 1);

        var redeemed = rewardService.redeem(reward.getId(), user);
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();

        rewardService.refund(redeemed.redemption().getId(), user);

        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(1);
        rewardService.redeem(reward.getId(), user);
    }

    /** Yesterday's spend belongs to yesterday; it must not eat into today's allowance. */
    @Test
    void anEarlierPeriodsRedemptionsDoNotCountAgainstThisOne() {
        setToday(THURSDAY);
        UUID user = richUser();
        Reward reward = rewardService.create(user, "Coffee", 50, "coffee", RewardTier.MICRO, true, 2);

        rewardService.redeem(reward.getId(), user);
        rewardService.redeem(reward.getId(), user);
        assertThat(viewOf(user, reward.getId()).remaining()).isZero();

        setToday(THURSDAY.plusDays(1));

        assertThat(viewOf(user, reward.getId()).remaining()).isEqualTo(2);
    }
}
