package com.dailyforge.insight.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.goal.domain.Goal;
import com.dailyforge.goal.domain.GoalService;
import com.dailyforge.goal.domain.GoalStatus;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.run.domain.RunService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The automatic recap half of "milestones for every month, yearly" (owner feedback) —
 * a month or year's own totals, computed from what is already logged rather than a
 * separate tracked entity. Reuses {@link DailySummaryService}'s own per-day rows (the
 * same ones the heatmap reads) for points and workout/run day-counts, and each owning
 * module's own service for the two counts a daily summary does not carry (habit ticks,
 * run distance, goals completed) — never another module's repository directly.
 */
@Service
public class MilestoneService {

    private final DailySummaryService dailySummaries;
    private final HabitLogService habitLogService;
    private final RunService runService;
    private final GoalService goalService;
    private final DayService dayService;
    private final IdentityService identity;

    public MilestoneService(
            DailySummaryService dailySummaries,
            HabitLogService habitLogService,
            RunService runService,
            GoalService goalService,
            DayService dayService,
            IdentityService identity) {
        this.dailySummaries = dailySummaries;
        this.habitLogService = habitLogService;
        this.runService = runService;
        this.goalService = goalService;
        this.dayService = dayService;
        this.identity = identity;
    }

    public enum RecapPeriod {
        MONTH,
        YEAR
    }

    public record Recap(
            LocalDate startDate,
            LocalDate endDate,
            int totalPoints,
            Map<PointsCategory, Integer> byCategory,
            int workoutDays,
            int runDays,
            int runDistanceMeters,
            long habitsCompleted,
            long goalsCompleted) {}

    @Transactional(readOnly = true)
    public Recap recap(UUID userId, RecapPeriod period, LocalDate anchor) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        LocalDate start = period == RecapPeriod.YEAR ? anchor.with(TemporalAdjusters.firstDayOfYear()) : anchor.withDayOfMonth(1);
        LocalDate periodEnd =
                period == RecapPeriod.YEAR ? anchor.with(TemporalAdjusters.lastDayOfYear()) : anchor.with(TemporalAdjusters.lastDayOfMonth());
        // A recap for the current, still-open month/year only covers what has actually
        // happened so far — the same "never walk past today" rule the heatmap follows.
        LocalDate end = periodEnd.isAfter(today) ? today : periodEnd;

        List<DailySummary> days = dailySummaries.heatmap(userId, start, end);

        int totalPoints = 0;
        Map<PointsCategory, Integer> byCategory = new EnumMap<>(PointsCategory.class);
        int workoutDays = 0;
        int runDays = 0;
        for (DailySummary day : days) {
            totalPoints += day.pointsTotal();
            day.pointsByCategory().forEach((category, amount) -> byCategory.merge(category, amount, Integer::sum));
            if (day.hasWorkout()) {
                workoutDays++;
            }
            if (day.hasRun()) {
                runDays++;
            }
        }

        long habitsCompleted = habitLogService.countDoneBetweenForUser(userId, start, end);
        int runDistanceMeters = runService.totalDistanceMeters(userId, start, end);

        ZonedDateTime rangeStart = start.atStartOfDay(zone);
        ZonedDateTime rangeEndExclusive = end.plusDays(1).atStartOfDay(zone);
        // Status COMPLETED already means complete — Goal.isComplete() instead answers
        // "has progress reached target", which is meaningless (always false) for a
        // CUSTOM goal, whose only targetValue is null; filtering on it here would drop
        // every completed CUSTOM goal from the recap.
        long goalsCompleted =
                goalService.list(userId, GoalStatus.COMPLETED).stream()
                        .filter(
                                g -> {
                                    if (g.getCompletedAt() == null) {
                                        return false;
                                    }
                                    ZonedDateTime completedAt = g.getCompletedAt().atZone(zone);
                                    return !completedAt.isBefore(rangeStart) && completedAt.isBefore(rangeEndExclusive);
                                })
                        .count();

        return new Recap(start, end, totalPoints, byCategory, workoutDays, runDays, runDistanceMeters, habitsCompleted, goalsCompleted);
    }
}
