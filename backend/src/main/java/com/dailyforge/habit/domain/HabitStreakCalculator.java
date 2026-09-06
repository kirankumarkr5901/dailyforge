package com.dailyforge.habit.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure streak arithmetic: no repositories, no clock, no Spring. Given the raw shape of a
 * habit's schedule and its logs, this says exactly what {@link HabitReconciliationCalculator}
 * needs to know — where the streak stands, which dates newly reached a bonus threshold,
 * and which scheduled dates were missed — all recomputed from scratch every time, never
 * accumulated, which is the property {@code ReconciliationEnginePropertyTest} depends on.
 *
 * A streak resets to zero on a miss, and so does the multiple-of-7 counter: a fresh run
 * earns its own 7-day, 14-day bonuses again rather than picking up where a broken streak
 * left off (spec §5.4 — the bonus is a property of "the streak", and a broken streak is
 * a new one).
 *
 * Today only ever extends the streak when it is already DONE; a merely pending or
 * unlogged today does not yet count as missed. That is what lets the habit board show
 * "today" as pending rather than red before the user has acted (spec §8.4).
 */
public final class HabitStreakCalculator {

    private HabitStreakCalculator() {}

    public record BonusEvent(LocalDate date, int multipleOfSeven) {}

    public record Result(
            int currentStreak,
            int bestStreak,
            int lastAwardedMultipleOf7,
            LocalDate lastCompletedDate,
            List<LocalDate> doneDates,
            List<BonusEvent> bonusEvents,
            List<LocalDate> missedDates) {}

    /**
     * @param scheduleDays the habit's schedule bitmask
     * @param from         the earliest date to consider (inclusive)
     * @param today        the boundary; dates after this are not walked at all
     * @param logs         every log this habit has from {@code from} onward
     */
    public static Result compute(
            int scheduleDays, LocalDate from, LocalDate today, Map<LocalDate, HabitLogState> logs) {
        int currentStreak = 0;
        int bestStreak = 0;
        int lastAwardedMultipleOf7 = 0;
        LocalDate lastCompletedDate = null;
        List<LocalDate> doneDates = new ArrayList<>();
        List<BonusEvent> bonusEvents = new ArrayList<>();
        List<LocalDate> missedDates = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            if (!ScheduleDays.isScheduled(scheduleDays, date)) {
                continue;
            }

            HabitLogState state = logs.get(date);
            boolean isToday = date.isEqual(today);

            if (state == HabitLogState.DONE) {
                currentStreak++;
                doneDates.add(date);
                lastCompletedDate = date;
                bestStreak = Math.max(bestStreak, currentStreak);

                if (currentStreak % 7 == 0) {
                    int n = currentStreak / 7;
                    if (n > lastAwardedMultipleOf7) {
                        lastAwardedMultipleOf7 = n;
                        bonusEvents.add(new BonusEvent(date, n));
                    }
                }
            } else if (isToday) {
                // Pending or explicitly skipped, but the day is not over yet: it does not
                // (yet) break anything. The walk simply stops here.
                break;
            } else {
                // A scheduled day before today with no DONE log — missed, whether it was
                // explicitly SKIPPED or never logged at all.
                missedDates.add(date);
                currentStreak = 0;
                lastAwardedMultipleOf7 = 0;
            }
        }

        return new Result(currentStreak, bestStreak, lastAwardedMultipleOf7, lastCompletedDate, doneDates, bonusEvents, missedDates);
    }

    /**
     * The consistency bonus formula (spec §5.4): {@code round(baseBonus * multiplier^(n-1))},
     * n capped at {@code maxExponent} so the number does not explode — past the cap the
     * same, pinned amount keeps paying every 7 days rather than continuing to grow.
     */
    public static int bonusAmount(int baseBonus, java.math.BigDecimal multiplier, int n, int maxExponent) {
        int cappedN = Math.min(n, maxExponent);
        double amount = baseBonus * Math.pow(multiplier.doubleValue(), cappedN - 1);
        return (int) Math.round(amount);
    }
}
