package com.dailyforge.insight.api;

import com.dailyforge.insight.domain.DailySummary;
import com.dailyforge.insight.domain.DailySummaryService.CategoryGroup;
import com.dailyforge.insight.domain.DayState;
import com.dailyforge.insight.domain.HomeSummaryService.HomeSummary;
import com.dailyforge.insight.domain.Badge;
import com.dailyforge.insight.domain.BadgeMetric;
import com.dailyforge.insight.domain.BadgeService.BadgeProgress;
import com.dailyforge.insight.domain.BadgeService.ClaimResult;
import com.dailyforge.insight.domain.MilestoneService.Recap;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import java.time.Instant;
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

    /**
     * A badge and this user's standing against it.
     *
     * {@code value} and {@code threshold} travel as raw numbers rather than as a
     * percentage so the UI can render "12 of 15 days" — which tells you what to do
     * tonight — rather than "80%", which does not.
     */
    public record BadgeResponse(
            String code,
            String name,
            String description,
            /** What to do to earn it, in plain terms. Shown when the badge is opened. */
            String criteria,
            RecapPeriod period,
            BadgeMetric metric,
            long value,
            int threshold,
            int points,
            String icon,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean earned,
            boolean claimed,
            boolean claimable,
            Instant claimedAt) {

        public static BadgeResponse of(BadgeProgress progress) {
            Badge badge = progress.badge();
            return new BadgeResponse(
                    badge.getCode(),
                    badge.getName(),
                    badge.getDescription(),
                    badge.getCriteria(),
                    badge.getPeriod(),
                    badge.getMetric(),
                    progress.value(),
                    badge.getThreshold(),
                    badge.getPoints(),
                    badge.getIcon(),
                    progress.periodStart(),
                    progress.periodEnd(),
                    progress.earned(),
                    progress.claimed(),
                    progress.claimable(),
                    progress.claimedAt());
        }
    }

    /** The badge as it now stands, plus the points the claim moved. */
    public record BadgeClaimResponse(BadgeResponse badge, PointsEnvelope points) {

        public static BadgeClaimResponse of(ClaimResult result) {
            return new BadgeClaimResponse(BadgeResponse.of(result.badge()), PointsEnvelope.of(result.points()));
        }
    }
}
