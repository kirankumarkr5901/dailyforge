package com.dailyforge.habit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure logic, no database — the streak arithmetic that every ledger entry the habit
 * module writes ultimately depends on, so its edge cases are worth pinning down exactly
 * rather than only exercising through the full reconciliation stack.
 */
class HabitStreakCalculatorTest {

    private static final int EVERY_DAY = ScheduleDays.EVERY_DAY;
    private static final LocalDate DAY1 = LocalDate.of(2026, 3, 2); // a Monday

    private Map<LocalDate, HabitLogState> logs(LocalDate... doneDates) {
        Map<LocalDate, HabitLogState> map = new HashMap<>();
        for (LocalDate date : doneDates) {
            map.put(date, HabitLogState.DONE);
        }
        return map;
    }

    @Test
    void sevenConsecutiveDaysReachesTheFirstBonus() {
        LocalDate[] days = new LocalDate[7];
        for (int i = 0; i < 7; i++) {
            days[i] = DAY1.plusDays(i);
        }
        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, days[6], logs(days));

        assertThat(result.currentStreak()).isEqualTo(7);
        assertThat(result.bestStreak()).isEqualTo(7);
        assertThat(result.lastAwardedMultipleOf7()).isEqualTo(1);
        assertThat(result.bonusEvents()).extracting(e -> e.multipleOfSeven()).containsExactly(1);
        assertThat(result.bonusEvents().get(0).date()).isEqualTo(days[6]);
    }

    @Test
    void aMissOnDayNineBreaksAFourteenDayStreakBackToZero() {
        // Days 1-8 done, day 9 missed (matches spec's own example in §5.3).
        LocalDate[] done = new LocalDate[8];
        for (int i = 0; i < 8; i++) {
            done[i] = DAY1.plusDays(i);
        }
        LocalDate missedDay = DAY1.plusDays(8);
        LocalDate today = DAY1.plusDays(9);

        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, today, logs(done));

        assertThat(result.missedDates()).containsExactly(missedDay);
        assertThat(result.currentStreak()).isZero(); // reset by the miss, and today has no log yet
        assertThat(result.bestStreak()).isEqualTo(8); // the best run achieved still stands
    }

    @Test
    void breakingAStreakResetsTheBonusCounterSoANewRunEarnsSevenDayAgain() {
        // 7 done, miss, then 7 more done: two separate 7-day bonuses, not one continuous 14.
        Map<LocalDate, HabitLogState> log = new HashMap<>();
        LocalDate d = DAY1;
        for (int i = 0; i < 7; i++) {
            log.put(d, HabitLogState.DONE);
            d = d.plusDays(1);
        }
        LocalDate missedDay = d;
        d = d.plusDays(1); // skip the missed day
        LocalDate secondRunStart = d;
        for (int i = 0; i < 7; i++) {
            log.put(d, HabitLogState.DONE);
            d = d.plusDays(1);
        }
        LocalDate today = d.minusDays(1);

        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, today, log);

        assertThat(result.missedDates()).containsExactly(missedDay);
        assertThat(result.currentStreak()).isEqualTo(7);
        assertThat(result.bonusEvents()).hasSize(2);
        assertThat(result.bonusEvents()).allMatch(e -> e.multipleOfSeven() == 1);
    }

    @Test
    void aNonScheduledDayNeitherExtendsNorBreaksTheStreak() {
        // Weekdays only (Mon-Fri = bits 0-4 = 0b0011111 = 31); a Saturday miss must not count.
        int weekdaysOnly = 0b001_1111;
        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate friday = monday.plusDays(4);
        LocalDate saturday = monday.plusDays(5); // not scheduled — never logged
        LocalDate nextMonday = monday.plusDays(7);

        Map<LocalDate, HabitLogState> log = new HashMap<>();
        for (LocalDate date = monday; !date.isAfter(friday); date = date.plusDays(1)) {
            log.put(date, HabitLogState.DONE);
        }
        log.put(nextMonday, HabitLogState.DONE);

        var result = HabitStreakCalculator.compute(weekdaysOnly, monday, nextMonday, log);

        assertThat(result.missedDates()).doesNotContain(saturday);
        assertThat(result.currentStreak()).isEqualTo(6); // Mon-Fri + next Monday, unbroken
    }

    @Test
    void todayPendingWithNoLogYetDoesNotBreakTheStreakOrCountAsMissed() {
        LocalDate yesterday = DAY1.plusDays(6);
        LocalDate today = DAY1.plusDays(7);
        Map<LocalDate, HabitLogState> log = new HashMap<>();
        for (LocalDate date = DAY1; !date.isAfter(yesterday); date = date.plusDays(1)) {
            log.put(date, HabitLogState.DONE);
        }
        // today has no log at all yet.

        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, today, log);

        assertThat(result.missedDates()).isEmpty();
        assertThat(result.currentStreak()).isEqualTo(7); // yesterday's streak still stands
    }

    @Test
    void todayExplicitlySkippedAlsoDoesNotYetBreakTheStreak() {
        // The day is not over; a user could still change their mind before rollover closes it.
        Map<LocalDate, HabitLogState> log = new HashMap<>();
        log.put(DAY1, HabitLogState.DONE);
        log.put(DAY1.plusDays(1), HabitLogState.SKIPPED);

        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, DAY1.plusDays(1), log);

        assertThat(result.currentStreak()).isEqualTo(1); // yesterday's single day still counts
        assertThat(result.missedDates()).isEmpty();
    }

    @Test
    void bestStreakSurvivesAfterALaterMiss() {
        Map<LocalDate, HabitLogState> log = new HashMap<>();
        LocalDate d = DAY1;
        for (int i = 0; i < 10; i++) {
            log.put(d, HabitLogState.DONE);
            d = d.plusDays(1);
        }
        LocalDate missedDay = d; // day 11 missed
        LocalDate today = d.plusDays(1);
        log.put(today, HabitLogState.DONE); // one day restarted after the miss

        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, today, log);

        assertThat(result.bestStreak()).isEqualTo(10);
        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.missedDates()).containsExactly(missedDay);
    }

    @Test
    void bonusAmountFormulaMatchesSpecExample() {
        // Spec §8.2's own preview: "7 days -> 20, 14 days -> 30, 21 days -> 45, 28 days -> 68"
        // with the default baseBonus=20, multiplier=1.5.
        BigDecimal multiplier = new BigDecimal("1.5");
        assertThat(HabitStreakCalculator.bonusAmount(20, multiplier, 1, 12)).isEqualTo(20);
        assertThat(HabitStreakCalculator.bonusAmount(20, multiplier, 2, 12)).isEqualTo(30);
        assertThat(HabitStreakCalculator.bonusAmount(20, multiplier, 3, 12)).isEqualTo(45);
        assertThat(HabitStreakCalculator.bonusAmount(20, multiplier, 4, 12)).isEqualTo(68);
    }

    @Test
    void bonusAmountIsPinnedPastTheExponentCap() {
        BigDecimal multiplier = new BigDecimal("1.5");
        int atCap = HabitStreakCalculator.bonusAmount(20, multiplier, 12, 12);
        int wayPastCap = HabitStreakCalculator.bonusAmount(20, multiplier, 60, 12);

        // Without the cap this would be astronomically larger (spec's own worked example:
        // "a 1.5 multiplier pays ~2.4 million points at one year").
        assertThat(wayPastCap).isEqualTo(atCap);
    }

    @Test
    void anEmptyRangeProducesNoActivityAndNoCrash() {
        var result = HabitStreakCalculator.compute(EVERY_DAY, DAY1, DAY1.minusDays(1), Map.of());

        assertThat(result.currentStreak()).isZero();
        assertThat(result.bonusEvents()).isEmpty();
        assertThat(result.missedDates()).isEmpty();
    }
}
