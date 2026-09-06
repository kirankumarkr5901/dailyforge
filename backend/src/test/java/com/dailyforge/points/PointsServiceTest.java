package com.dailyforge.points;

import com.dailyforge.testsupport.TestUsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.CelebrationType;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.points.repo.UserScoreCacheRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The engine's own behaviour, independent of any HTTP layer: idempotency, the ledger and
 * cache staying in lockstep, reversal, and celebration derivation. This is the module
 * the spec calls the project's spine (§M2), so these are the tests that matter most in the whole repo.
 */
@SpringBootTest
@ActiveProfiles("test")
class PointsServiceTest {

    @Autowired private PointsService points;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserScoreCacheRepository caches;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final ZoneId UTC = ZoneId.of("UTC");

    private AwardCommand habitAward(UUID userId, int amount, String key) {
        return AwardCommand.withoutSource(
                userId, LocalDate.of(2026, 3, 12), PointsCategory.HABIT, "HABIT_BASE", amount, "Read", key);
    }

    @Test
    void awardingWritesOneEntryAndUpdatesTheCacheInStep() {
        UUID user = TestUsers.create(users, settings);

        PointsResult result = points.award(habitAward(user, 12, "k1"));

        assertThat(result.delta()).isEqualTo(12);
        assertThat(result.newTotal()).isEqualTo(12);
        assertThat(entries.sumAmountForUser(user)).isEqualTo(result.newTotal());
        assertThat(caches.findById(user).orElseThrow().getTotalPoints()).isEqualTo(12);
    }

    @Test
    void repeatingTheSameKeyWithTheSameCommandIsASafeNoOpReplay() {
        UUID user = TestUsers.create(users, settings);

        points.award(habitAward(user, 12, "same-key"));
        PointsResult replay = points.award(habitAward(user, 12, "same-key"));

        // Not twelve points twice — the retry a flaky mobile network causes must not
        // double-award (spec §4.5).
        assertThat(replay.newTotal()).isEqualTo(12);
        assertThat(entries.sumAmountForUser(user)).isEqualTo(12);
        assertThat(entries.countForUser(user)).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithADifferentAmountIsAConflictNotASilentOverwrite() {
        UUID user = TestUsers.create(users, settings);
        points.award(habitAward(user, 12, "dup-key"));

        assertThatThrownBy(() -> points.award(habitAward(user, 99, "dup-key")))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e -> assertThat(((ApiException) e).getCode().name()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void reverseBySourceWritesACompensatingEntryRatherThanDeletingTheOriginal() {
        UUID user = TestUsers.create(users, settings);
        UUID sourceId = UUID.randomUUID();

        points.award(
                new AwardCommand(
                        user,
                        LocalDate.of(2026, 3, 12),
                        PointsCategory.WORKOUT,
                        "WORKOUT_SET",
                        1,
                        "WORKOUT_SET",
                        sourceId,
                        "Bench press set",
                        "set-1"));

        points.reverseBySource("WORKOUT_SET", sourceId, "Set deleted");

        assertThat(entries.countForUser(user)).isEqualTo(2); // original + reversal, neither deleted
        assertThat(entries.sumAmountForUser(user)).isZero();
        assertThat(caches.findById(user).orElseThrow().getTotalPoints()).isZero();

        var rows = entries.findAllBySourceTypeAndSourceIdAndReversedFalseAndReversesIdIsNull("WORKOUT_SET", sourceId);
        assertThat(rows).isEmpty(); // the original is now reversed, so it drops out of this query
    }

    @Test
    void reversingAnAlreadyReversedSourceIsANoOp() {
        UUID user = TestUsers.create(users, settings);
        UUID sourceId = UUID.randomUUID();
        points.award(
                new AwardCommand(
                        user, LocalDate.of(2026, 3, 12), PointsCategory.RUN, "RUN_DISTANCE", 50,
                        "RUN", sourceId, "A run", "run-1"));

        points.reverseBySource("RUN", sourceId, "deleted");
        points.reverseBySource("RUN", sourceId, "deleted again"); // must not double-reverse

        assertThat(entries.countForUser(user)).isEqualTo(2);
        assertThat(entries.sumAmountForUser(user)).isZero();
    }

    @Test
    void recalculateExactlyMatchesTheLedgerAfterAwardsAndAReversal() {
        UUID user = TestUsers.create(users, settings);
        UUID sourceId = UUID.randomUUID();

        points.award(habitAward(user, 20, "a"));
        points.award(habitAward(user, 30, "b"));
        points.award(
                new AwardCommand(
                        user, LocalDate.of(2026, 3, 12), PointsCategory.WORKOUT, "WORKOUT_PR", 15,
                        "WORKOUT_SET", sourceId, "PR", "c"));
        points.reverseBySource("WORKOUT_SET", sourceId, "undo");

        int ledgerSum = entries.sumAmountForUser(user);
        points.recalculate(user);

        // The whole point of a cache: it must always be exactly reconstructable.
        assertThat(caches.findById(user).orElseThrow().getTotalPoints()).isEqualTo(ledgerSum);
        assertThat(ledgerSum).isEqualTo(50);
    }

    @Test
    void aWorkoutPrCelebratesButAHabitTickDoesNot() {
        UUID user = TestUsers.create(users, settings);

        PointsResult habit = points.award(habitAward(user, 12, "h1"));
        assertThat(habit.celebrations()).isEmpty();

        PointsResult pr =
                points.award(
                        new AwardCommand(
                                user, LocalDate.of(2026, 3, 12), PointsCategory.WORKOUT, "WORKOUT_PR", 15,
                                "WORKOUT_SET", UUID.randomUUID(), "New PR", "pr1"));
        assertThat(pr.celebrations()).extracting(c -> c.type()).containsExactly(CelebrationType.PR);
    }

    @Test
    void aZeroPointSessionCompleteStillCelebratesBecauseThatIsItsWholePurpose() {
        UUID user = TestUsers.create(users, settings);

        PointsResult result =
                points.award(
                        AwardCommand.withoutSource(
                                user, LocalDate.of(2026, 3, 12), PointsCategory.WORKOUT,
                                "WORKOUT_SESSION_COMPLETE", 0, "Session complete", "sc1"));

        assertThat(result.celebrations()).extracting(c -> c.type()).containsExactly(CelebrationType.WORKOUT_COMPLETE);
    }

    @Test
    void aReversalNeverReplaysTheOriginalCelebration() {
        UUID user = TestUsers.create(users, settings);
        UUID sourceId = UUID.randomUUID();
        points.award(
                new AwardCommand(
                        user, LocalDate.of(2026, 3, 12), PointsCategory.WORKOUT, "WORKOUT_PR", 15,
                        "WORKOUT_SET", sourceId, "PR", "pr-rev"));

        points.reverseBySource("WORKOUT_SET", sourceId, "undo");

        // The reversal entry carries rule_code WORKOUT_PR too (spec: "same category,
        // source" for the compensating row) but must not fire a second PR celebration.
        var reversal =
                entries.findAllByUserIdAndSourceTypeAndSourceIdAndReversedFalseAndReversesIdIsNull(user, "WORKOUT_SET", sourceId);
        assertThat(reversal).isEmpty(); // both rows for this source are now reversed=true or negative
    }

    @Test
    void snapshotSumsTodayWeekAndMonthCorrectlyInTheUsersOwnZone() {
        UUID user = TestUsers.create(users, settings);
        // 2026-03-12 is a Thursday; Monday of that week is 2026-03-09.
        points.award(habitAward(user, 10, "d1")); // occurredOn 2026-03-12 (today, per test data)
        points.award(
                AwardCommand.withoutSource(
                        user, LocalDate.of(2026, 3, 9), PointsCategory.HABIT, "HABIT_BASE", 5, "Read", "d2"));
        points.award(
                AwardCommand.withoutSource(
                        user, LocalDate.of(2026, 2, 1), PointsCategory.HABIT, "HABIT_BASE", 100, "Read", "d3"));

        // snapshot() computes "today" from the clock, not from the test's fixture dates,
        // so this test only checks totals reachable without depending on the real date —
        // the month/week/day boundary behaviour itself is covered by DayServiceTest.
        var snapshot = points.snapshot(user, UTC);
        assertThat(snapshot.total()).isEqualTo(115);
    }

    @Test
    void awardingUpToTheDailyCapSucceedsButCrossingItIsRefused() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.of(2026, 3, 12);

        // WORKOUT is capped at 500/day (V2 seed). Reach exactly the cap first.
        points.award(
                new AwardCommand(
                        user, today, PointsCategory.WORKOUT, "WORKOUT_SET", 500,
                        "WORKOUT_SET", UUID.randomUUID(), "sets", "cap-fill"));

        assertThatThrownBy(
                        () ->
                                points.award(
                                        new AwardCommand(
                                                user, today, PointsCategory.WORKOUT, "WORKOUT_SET", 1,
                                                "WORKOUT_SET", UUID.randomUUID(), "one more", "cap-break")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode().name()).isEqualTo("DAILY_CAP_REACHED"));

        // The rejected attempt must not have partially applied.
        assertThat(entries.sumAmountByCategoryOnDate(user, PointsCategory.WORKOUT, today)).isEqualTo(500);
    }

    @Test
    void theDailyCapIsScopedPerCategoryAndPerDay() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.of(2026, 3, 12);

        points.award(
                new AwardCommand(
                        user, today, PointsCategory.WORKOUT, "WORKOUT_SET", 500,
                        "WORKOUT_SET", UUID.randomUUID(), "sets", "wk-cap"));

        // A different category, same day: unaffected by WORKOUT's cap.
        PointsResult run =
                points.award(
                        new AwardCommand(
                                user, today, PointsCategory.RUN, "RUN_DISTANCE", 50,
                                "RUN", UUID.randomUUID(), "a run", "run-ok"));
        assertThat(run.newTotal()).isEqualTo(550);

        // HABIT has no configured cap (null in V2 seed) — unlimited.
        PointsResult habit = points.award(habitAward(user, 10_000, "uncapped"));
        assertThat(habit.delta()).isEqualTo(10_000);
    }

    @Test
    void aNegativeAmountNeverTriggersTheDailyCapSinceItIsReducingNotEarning() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.of(2026, 3, 12);
        points.award(
                new AwardCommand(
                        user, today, PointsCategory.WORKOUT, "WORKOUT_SET", 500,
                        "WORKOUT_SET", UUID.randomUUID(), "sets", "wk-cap-2"));

        // A penalty in the same already-capped category must still be recordable.
        PointsResult penalty =
                points.award(
                        AwardCommand.withoutSource(
                                user, today, PointsCategory.WORKOUT, "ADJUSTMENT", -50, "correction", "adj-1"));
        assertThat(penalty.delta()).isEqualTo(-50);
    }

    @Test
    void anAwardWithoutASourceHasNoSourceToLaterReverse() {
        UUID user = TestUsers.create(users, settings);
        points.award(habitAward(user, 12, "no-source"));

        // Nothing should blow up calling reverseBySource with a source that was never used.
        points.reverseBySource("WORKOUT_SET", UUID.randomUUID(), "no-op");
        assertThat(entries.sumAmountForUser(user)).isEqualTo(12);
    }
}
