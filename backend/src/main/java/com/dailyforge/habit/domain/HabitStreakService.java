package com.dailyforge.habit.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.habit.repo.HabitStreakRepository;
import com.dailyforge.identity.domain.IdentityService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that reads {@code habit_log} and runs {@link HabitStreakCalculator}.
 * {@link HabitReconciliationCalculator} uses the result to decide what the ledger should
 * contain; the habit board reads the cached {@code habit_streak} row this rebuilds for
 * fast display — but both trace back to this same computation, so they can never
 * disagree with each other by construction.
 */
@Service
public class HabitStreakService {

    private final HabitLogRepository logs;
    private final HabitStreakRepository streaks;
    private final IdentityService identity;
    private final DayService dayService;

    public HabitStreakService(
            HabitLogRepository logs,
            HabitStreakRepository streaks,
            IdentityService identity,
            DayService dayService) {
        this.logs = logs;
        this.streaks = streaks;
        this.identity = identity;
        this.dayService = dayService;
    }

    /** From {@code max(habit.activeFrom, from)} through the habit owner's local today. */
    public HabitStreakCalculator.Result compute(Habit habit, LocalDate from) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(habit.getUserId()).getTimeZone());
        LocalDate today = dayService.today(zone);
        LocalDate start = from.isAfter(habit.getActiveFrom()) ? from : habit.getActiveFrom();

        if (start.isAfter(today)) {
            return HabitStreakCalculator.compute(habit.getScheduleDays(), today, today.minusDays(1), Map.of());
        }

        Map<LocalDate, HabitLogState> byDate =
                logs.findAllByHabitIdAndOccurredOnGreaterThanEqualOrderByOccurredOnAsc(habit.getId(), start).stream()
                        .collect(Collectors.toMap(HabitLog::getOccurredOn, HabitLog::getState));

        return HabitStreakCalculator.compute(habit.getScheduleDays(), start, today, byDate);
    }

    /** Rebuilds the display cache from scratch — never incremented, always recomputed. */
    @Transactional
    public void rebuildCache(Habit habit) {
        HabitStreakCalculator.Result result = compute(habit, habit.getActiveFrom());

        HabitStreak streak = streaks.findById(habit.getId()).orElseGet(() -> HabitStreak.empty(habit.getId()));
        streak.rebuild(
                result.currentStreak(), result.bestStreak(), result.lastAwardedMultipleOf7(), result.lastCompletedDate());
        streaks.save(streak);
    }
}
