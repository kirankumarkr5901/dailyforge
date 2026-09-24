package com.dailyforge.insight.api;

import com.dailyforge.insight.domain.DailySummary;
import com.dailyforge.insight.domain.DailySummaryService.CategoryGroup;
import com.dailyforge.insight.domain.DayState;
import com.dailyforge.insight.domain.HomeSummaryService.HomeSummary;
import com.dailyforge.insight.domain.MilestoneService.Recap;
import com.dailyforge.insight.domain.Quote;
import com.dailyforge.goal.api.GoalDtos.GoalResponse;
import com.dailyforge.points.api.PointsDtos.LedgerEntryResponse;
import com.dailyforge.points.api.PointsDtos.SnapshotResponse;
import com.dailyforge.points.domain.PointsCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InsightDtos {

    private InsightDtos() {}

    public record QuoteResponse(UUID id, String text, String author, String source) {
        public static QuoteResponse of(Quote quote) {
            return quote == null ? null : new QuoteResponse(quote.getId(), quote.getText(), quote.getAuthor(), quote.getSource());
        }
    }

    public record HomeSummaryResponse(
            LocalDate date,
            QuoteResponse quote,
            SnapshotResponse score,
            List<GoalResponse> activeGoals,
            List<LedgerEntryResponse> recentLedger) {
        public static HomeSummaryResponse of(HomeSummary summary) {
            return new HomeSummaryResponse(
                    summary.date(),
                    QuoteResponse.of(summary.quote()),
                    SnapshotResponse.of(summary.score()),
                    summary.activeGoals().stream().map(GoalResponse::of).toList(),
                    summary.recentLedger().stream().map(LedgerEntryResponse::of).toList());
        }
    }

    public record DailySummaryResponse(
            LocalDate date,
            int pointsTotal,
            Map<PointsCategory, Integer> pointsByCategory,
            boolean hasWorkout,
            boolean hasRun,
            boolean hasHabitCompletion,
            int inactiveRunLength,
            DayState state) {

        public static DailySummaryResponse of(DailySummary summary) {
            return new DailySummaryResponse(
                    summary.date(),
                    summary.pointsTotal(),
                    summary.pointsByCategory(),
                    summary.hasWorkout(),
                    summary.hasRun(),
                    summary.hasHabitCompletion(),
                    summary.inactiveRunLength(),
                    summary.state());
        }
    }

    public record CategoryGroupResponse(PointsCategory category, int total, List<LedgerEntryResponse> entries) {
        public static CategoryGroupResponse of(CategoryGroup group) {
            return new CategoryGroupResponse(
                    group.category(), group.total(), group.entries().stream().map(LedgerEntryResponse::of).toList());
        }
    }

    public record DayDetailResponse(LocalDate date, int total, List<CategoryGroupResponse> groups) {}

    public record MilestoneRecapResponse(
            LocalDate startDate,
            LocalDate endDate,
            int totalPoints,
            Map<PointsCategory, Integer> byCategory,
            int workoutDays,
            int runDays,
            int runDistanceMeters,
            long habitsCompleted,
            long goalsCompleted) {
        public static MilestoneRecapResponse of(Recap recap) {
            return new MilestoneRecapResponse(
                    recap.startDate(),
                    recap.endDate(),
                    recap.totalPoints(),
                    recap.byCategory(),
                    recap.workoutDays(),
                    recap.runDays(),
                    recap.runDistanceMeters(),
                    recap.habitsCompleted(),
                    recap.goalsCompleted());
        }
    }
}
