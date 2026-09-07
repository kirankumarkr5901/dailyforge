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

/** Spending points (spec §5.4, §8.9) — the sink that closes the plan's own core loop. */
@SpringBootTest
@Import(RewardClockTestConfig.class)
@ActiveProfiles("test")
class RewardServiceTest {

    @Autowired private RewardService rewardService;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private RewardClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private void grant(UUID user, int amount) {
        points.award(
                AwardCommand.withoutSource(user, TODAY, PointsCategory.ADJUSTMENT, "TEST_GRANT", amount, "test grant", "grant:" + UUID.randomUUID()));
    }

    @Test
    void redeemingDeductsTheRewardsCost() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 500);
        Reward reward = rewardService.create(user, "Movie night", 300, "clapperboard", RewardTier.MICRO, true, null);

        var result = rewardService.redeem(reward.getId(), user);

        assertThat(result.points().delta()).isEqualTo(-300);
        assertThat(result.points().newTotal()).isEqualTo(200);
    }

    @Test
    void redeemingWithoutEnoughPointsIsRefusedWithTheSpecsOwnMessageShape() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 340);
        Reward reward = rewardService.create(user, "Expensive thing", 500, "gift", RewardTier.MICRO, true, null);

        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Costs 500")
                .hasMessageContaining("You have 340");
    }

    @Test
    void aOneOffRewardCannotBeRedeemedTwiceUntilRefunded() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 1000);
        Reward reward = rewardService.create(user, "One-time treat", 100, "star", RewardTier.MICRO, false, null);

        var first = rewardService.redeem(reward.getId(), user);
        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user)).isInstanceOf(ApiException.class);

        rewardService.refund(first.redemption().getId(), user);
        var second = rewardService.redeem(reward.getId(), user); // now allowed again
        assertThat(second.points().delta()).isEqualTo(-100);
    }

    @Test
    void stockDecrementsOnRedemptionAndBlocksAtZero() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 1000);
        Reward reward = rewardService.create(user, "Limited edition", 50, "star", RewardTier.MICRO, true, 1);

        rewardService.redeem(reward.getId(), user);

        assertThatThrownBy(() -> rewardService.redeem(reward.getId(), user)).isInstanceOf(ApiException.class);
    }

    @Test
    void refundingTheSameDayRestoresThePointsAndTheStock() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 1000);
        Reward reward = rewardService.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, 3);

        var redeemed = rewardService.redeem(reward.getId(), user);
        assertThat(redeemed.points().newTotal()).isEqualTo(950);

        var refundResult = rewardService.refund(redeemed.redemption().getId(), user);

        assertThat(refundResult.newTotal()).isEqualTo(1000);
        // Stock is back to 3 — a fresh redemption must succeed rather than hitting "out of stock".
        rewardService.redeem(reward.getId(), user);
    }

    @Test
    void refundingOnADifferentDayIsRefused() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 1000);
        Reward reward = rewardService.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, null);
        var redeemed = rewardService.redeem(reward.getId(), user);

        setToday(TODAY.plusDays(1));

        assertThatThrownBy(() -> rewardService.refund(redeemed.redemption().getId(), user)).isInstanceOf(ApiException.class);
    }

    @Test
    void oneUserCannotReachAnotherUsersReward() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        Reward reward = rewardService.create(userA, "A's reward", 10, "star", RewardTier.MICRO, true, null);

        assertThatThrownBy(() -> rewardService.requireOwned(reward.getId(), userB)).isInstanceOf(ApiException.class);
    }

    /** Owner feedback: "Rewards also should be editable". */
    @Test
    void editingARewardChangesTheNextRedemptionButNotThePastOne() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        grant(user, 1000);
        Reward reward = rewardService.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, null);

        var firstRedemption = rewardService.redeem(reward.getId(), user);
        assertThat(firstRedemption.redemption().getPointsSpent()).isEqualTo(50);

        rewardService.update(reward.getId(), user, "Bigger snack", 80, "cookie", RewardTier.WEEKLY, true, null);

        Reward reloaded = rewardService.requireOwned(reward.getId(), user);
        assertThat(reloaded.getName()).isEqualTo("Bigger snack");
        assertThat(reloaded.getCost()).isEqualTo(80);
        assertThat(reloaded.getTier()).isEqualTo(RewardTier.WEEKLY);

        // The ledger is append-only: what was already charged stays charged at the old
        // price, and only the next redemption pays the new one.
        assertThat(firstRedemption.redemption().getPointsSpent()).isEqualTo(50);
        assertThat(rewardService.redeem(reward.getId(), user).redemption().getPointsSpent()).isEqualTo(80);
    }

    @Test
    void oneUserCannotEditAnotherUsersReward() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        Reward reward = rewardService.create(userA, "A's reward", 10, "star", RewardTier.MICRO, true, null);

        assertThatThrownBy(() -> rewardService.update(reward.getId(), userB, "Stolen", 1, "star", RewardTier.MICRO, true, null))
                .isInstanceOf(ApiException.class);
    }
}
