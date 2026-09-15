package com.dailyforge.habit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.habit.repo.HabitStreakRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec §5.3's own worked example, end to end through the real habit module: "a 14-day
 * habit streak earned a consistency bonus; the user then unticks day 9. The 14-day bonus
 * is no longer valid." This is what the M2 property test proved the mechanism could do
 * in the abstract; this proves the real {@code HabitReconciliationCalculator} does it.
 *
 * The untick happens while day 9 is still "yesterday" relative to the clock, respecting
 * the today/yesterday edit window a real user is bound by (spec §4.3) — the spec's
 * example describes what the mechanism must do when a past day changes, not a claim that
 * an already-fully-elapsed 14-day-old day is directly editable through the ordinary API.
 */
@SpringBootTest
@Import(HabitClockTestConfig.class)
@ActiveProfiles("test")
class HabitReconciliationIntegrationTest {

    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private HabitStreakRepository streaks;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private HabitClockTestConfig.MutableClock clock;

    private static final LocalDate DAY1 = LocalDate.of(2026, 3, 2); // a Monday

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private void tickThrough(Habit habit, UUID user, int throughDayIndexInclusive) {
        for (int i = 0; i <= throughDayIndexInclusive; i++) {
            LocalDate date = DAY1.plusDays(i);
            setToday(date);
            habitLogService.log(habit.getId(), user, date);
        }
    }

    @Test
    void aFourteenDayStreakEarnsABonusAndUntickingDayNineInvalidatesIt() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        Habit habit =
                habitService.create(
                        user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new java.math.BigDecimal("1.5"), 127, DAY1);

        tickThrough(habit, user, 8); // days 1-9 (index 0-8)
        LocalDate day9 = DAY1.plusDays(8);

        // Day 10 is now "today", so day 9 is "yesterday" — still within the edit window
        // (spec §4.3) — when the user changes their mind and unticks it.
        setToday(DAY1.plusDays(9));
        habitLogService.unlog(habit.getId(), user, day9);

        // Continue ticking days 10-14 as they arrive, with day 9 permanently un-ticked.
        for (int i = 9; i <= 13; i++) {
            LocalDate date = DAY1.plusDays(i);
            setToday(date);
            habitLogService.log(habit.getId(), user, date);
        }

        // Days 1-8 still stand (80 points, one 7-day bonus = 20). Days 10-14 form a new,
        // separate 5-day run that has not reached 7 yet.
        int totalAfterUntick = entries.sumAmountForUser(user);
        assertThat(totalAfterUntick).isEqualTo(80 + 20 + 50); // 8 base days + 7-day bonus + 5 new base days

        var streakAfterUntick = streaks.findById(habit.getId()).orElseThrow();
        assertThat(streakAfterUntick.getCurrentStreak()).isEqualTo(5); // days 10-14
        assertThat(streakAfterUntick.getBestStreak()).isEqualTo(8); // the best run that ever stood
    }

    @Test
    void reTickingDayNineRestoresTheFullStreakAndItsBonusesAgain() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        Habit habit =
                habitService.create(
                        user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new java.math.BigDecimal("1.5"), 127, DAY1);

        tickThrough(habit, user, 8); // days 1-9
        LocalDate day9 = DAY1.plusDays(8);

        setToday(DAY1.plusDays(9)); // day 9 is "yesterday" here, still editable
        habitLogService.unlog(habit.getId(), user, day9);
        habitLogService.log(habit.getId(), user, day9); // changed their mind again, same session

        for (int i = 9; i <= 13; i++) {
            LocalDate date = DAY1.plusDays(i);
            setToday(date);
            habitLogService.log(habit.getId(), user, date);
        }

        // Fully restored: the same 190 total the unbroken streak first earned, even
        // though the ledger took a detour of reversals to get back there.
        assertThat(entries.sumAmountForUser(user)).isEqualTo(190);
        assertThat(streaks.findById(habit.getId()).orElseThrow().getCurrentStreak()).isEqualTo(14);
    }

    @Test
    void aStrictHabitMissedAfterADayClosesEarnsAPenaltyThatANormalHabitDoesNot() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        Habit strict =
                habitService.create(
                        user, "Cold shower", "drop", 10, HabitType.STRICT, 15, 20, new java.math.BigDecimal("1.5"), 127, DAY1);
        Habit normal =
                habitService.create(
                        user, "Stretch", "yoga", 10, HabitType.NORMAL, 0, 20, new java.math.BigDecimal("1.5"), 127, DAY1);

        // Both ticked day 1. Day 2 is deliberately never logged for either habit.
        habitLogService.log(strict.getId(), user, DAY1);
        habitLogService.log(normal.getId(), user, DAY1);

        // Day 3 arrives — day 2 has now genuinely closed without a log, so reconciling
        // either habit's own scope must recompute day 2 as missed.
        setToday(DAY1.plusDays(2));
        habitLogService.log(strict.getId(), user, DAY1.plusDays(2));
        habitLogService.log(normal.getId(), user, DAY1.plusDays(2));

        var allEntries = entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "HABIT_LOG");

        boolean strictHasPenalty =
                allEntries.stream().anyMatch(e -> e.getRuleCode().equals("HABIT_PENALTY") && e.getAmount() == -15);
        assertThat(strictHasPenalty).isTrue();

        // The normal habit's streak breaks the same way but never incurs a penalty —
        // and, critically, its own entries must be untouched by the STRICT habit's
        // reconciliation despite both sharing the "HABIT_LOG" source type.
        boolean anyPenaltyOnNormal =
                allEntries.stream().anyMatch(e -> e.getDescription().contains("Stretch") && e.getAmount() < 0);
        assertThat(anyPenaltyOnNormal).isFalse();

        long normalBaseEntries =
                allEntries.stream()
                        .filter(e -> e.getDescription().equals("Stretch") && e.getRuleCode().equals("HABIT_BASE"))
                        .count();
        assertThat(normalBaseEntries).isEqualTo(2); // day 1 and day 3 — neither reversed by the strict habit's own reconcile
    }
}
