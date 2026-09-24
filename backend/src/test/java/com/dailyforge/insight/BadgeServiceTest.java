package com.dailyforge.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.insight.domain.BadgeService;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Earning a badge, and taking the points for it.
 *
 * The badges under test are inserted here rather than taken from the seeded catalogue.
 * The real thresholds are deliberately hard — fifteen workout days in a month — and a
 * test that reproduced them would be slow, and would break every time the economy was
 * rebalanced. What matters here is the mechanism: the threshold, the claim, and the
 * refusals. A badge with a threshold of one exercises all three.
 */
@SpringBootTest
@Import(InsightClockTestConfig.class)
@ActiveProfiles("test")
class BadgeServiceTest {

    @Autowired private BadgeService badges;
    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private InsightClockTestConfig.MutableClock clock;

    private static final LocalDate REAL_TODAY = LocalDate.now(ZoneOffset.UTC);

    private UUID user;

    @BeforeEach
    void setUp() {
        clock.set(REAL_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
        user = TestUsers.create(users, settings);
    }

    /** A badge of our own, so the assertions do not depend on the shipped thresholds. */
    private String seedBadge(String code, int threshold, int points) {
        jdbc.update(
                """
                INSERT INTO badge (id, code, name, description, criteria, period, metric, threshold, points, icon,
                                   sort_order, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'MONTH', 'HABITS_COMPLETED', ?, ?, 'check', 1, TRUE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                code,
                "Test " + code,
                "A badge used by the tests.",
                "Complete " + threshold + " habit ticks this month.",
                threshold,
                points);
        return code;
    }

    private void tickAHabitToday() {
        var habit =
                habitService.create(
                        user, "Read " + UUID.randomUUID(), "book", 10, HabitType.NORMAL, 0, 0, BigDecimal.ONE, 127, REAL_TODAY);
        habitLogService.log(habit.getId(), user, REAL_TODAY);
    }

    private BadgeService.BadgeProgress progressFor(String code) {
        return badges.progress(user, RecapPeriod.MONTH, REAL_TODAY).stream()
                .filter(p -> p.badge().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aBadgeShowsHowFarAlongItIsBeforeItIsEarned() {
        String code = seedBadge("TEST_FAR_OFF", 5, 100);

        var progress = progressFor(code);

        assertThat(progress.value()).isZero();
        assertThat(progress.badge().getThreshold()).isEqualTo(5);
        assertThat(progress.earned()).isFalse();
        assertThat(progress.claimable()).isFalse();
    }

    /**
     * Crossing the threshold is not the same as being paid. The owner chose a claim step
     * precisely so the payout is a moment the user takes, rather than a number that
     * moved on its own while they were not looking.
     */
    @Test
    void crossingTheThresholdMakesABadgeClaimableButPaysNothingYet() {
        String code = seedBadge("TEST_EARNED", 1, 100);
        int before = points.snapshot(user, ZoneOffset.UTC).total();

        tickAHabitToday();

        var progress = progressFor(code);
        assertThat(progress.earned()).isTrue();
        assertThat(progress.claimed()).isFalse();
        assertThat(progress.claimable()).isTrue();

        // The habit tick itself paid, but the badge has not.
        int after = points.snapshot(user, ZoneOffset.UTC).total();
        assertThat(after - before).isEqualTo(10);
    }

    @Test
    void claimingPaysExactlyTheBadgesOwnPointsAndMarksItClaimed() {
        String code = seedBadge("TEST_CLAIM", 1, 250);
        tickAHabitToday();
        int before = points.snapshot(user, ZoneOffset.UTC).total();

        var result = badges.claim(user, code, REAL_TODAY);

        assertThat(result.points().delta()).isEqualTo(250);
        assertThat(result.points().newTotal()).isEqualTo(before + 250);
        assertThat(result.badge().claimed()).isTrue();
        assertThat(result.badge().claimable()).isFalse();
        assertThat(progressFor(code).claimed()).isTrue();
    }

    /** Twice would be free points. The award row's unique constraint is the real guard. */
    @Test
    void aBadgeCannotBeClaimedTwiceInTheSamePeriod() {
        String code = seedBadge("TEST_ONCE", 1, 250);
        tickAHabitToday();
        badges.claim(user, code, REAL_TODAY);
        int after = points.snapshot(user, ZoneOffset.UTC).total();

        assertThatThrownBy(() -> badges.claim(user, code, REAL_TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already claimed");

        assertThat(points.snapshot(user, ZoneOffset.UTC).total()).isEqualTo(after);
    }

    /**
     * Refused, and the refusal repeats what it would take — a bare "you cannot do that"
     * leaves the user staring at a button with no idea what is missing.
     */
    @Test
    void claimingABadgeYouHaveNotEarnedIsRefusedAndSaysWhatItTakes() {
        String code = seedBadge("TEST_UNEARNED", 9, 100);

        assertThatThrownBy(() -> badges.claim(user, code, REAL_TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Complete 9 habit ticks");

        assertThat(progressFor(code).claimed()).isFalse();
    }

    @Test
    void claimingABadgeThatDoesNotExistIsANotFound() {
        assertThatThrownBy(() -> badges.claim(user, "NO_SUCH_BADGE", REAL_TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not exist");
    }

    /**
     * The same badge in a different month is a different badge award. This is the whole
     * reason the award row is keyed by period start rather than just by code: a monthly
     * badge that could only ever be earned once would be a one-off trophy, not a monthly
     * one.
     */
    @Test
    void theSameBadgeIsClaimableAgainInANewPeriod() {
        String code = seedBadge("TEST_MONTHLY", 1, 250);
        tickAHabitToday();
        badges.claim(user, code, REAL_TODAY);

        LocalDate lastMonth = REAL_TODAY.minusMonths(1);
        var previous =
                badges.progress(user, RecapPeriod.MONTH, lastMonth).stream()
                        .filter(p -> p.badge().getCode().equals(code))
                        .findFirst()
                        .orElseThrow();

        // A different period, so the claim from this month does not carry over — the
        // badge is unclaimed there, whatever its progress happens to be.
        assertThat(previous.periodStart()).isEqualTo(lastMonth.withDayOfMonth(1));
        assertThat(previous.claimed()).isFalse();
    }

    /** The period recorded is the whole month, not "the 1st to today". */
    @Test
    void anAwardRecordsTheWholePeriodItBelongsTo() {
        String code = seedBadge("TEST_PERIOD", 1, 100);
        tickAHabitToday();

        var claimed = badges.claim(user, code, REAL_TODAY).badge();

        assertThat(claimed.periodStart()).isEqualTo(REAL_TODAY.withDayOfMonth(1));
        assertThat(claimed.periodEnd()).isEqualTo(REAL_TODAY.withDayOfMonth(REAL_TODAY.lengthOfMonth()));
    }
}
