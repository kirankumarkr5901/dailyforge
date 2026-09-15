package com.dailyforge.insight.domain;

import com.dailyforge.points.domain.PointsCategory;
import java.time.LocalDate;
import java.util.Map;

/**
 * One heatmap cell (spec §8.1.1). Computed on demand from the points ledger rather than
 * a materialized {@code daily_summary} table — the same call M4 and M5 made for a PR
 * view: a pure display value, not part of the points-invariant chain.
 */
public record DailySummary(
        LocalDate date,
        int pointsTotal,
        Map<PointsCategory, Integer> pointsByCategory,
        boolean hasWorkout,
        boolean hasRun,
        boolean hasHabitCompletion,
        int inactiveRunLength,
        DayState state) {

    static DailySummary empty(LocalDate date) {
        return new DailySummary(date, 0, Map.of(), false, false, false, 0, DayState.EMPTY);
    }
}
