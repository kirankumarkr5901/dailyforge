package com.dailyforge.insight.domain;

import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.nutrition.domain.NutritionSettings;
import com.dailyforge.nutrition.domain.ProteinLog;
import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import com.dailyforge.nutrition.repo.ProteinLogRepository;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.workout.domain.WeeklyVolumeService;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One Monday-to-Sunday week in a few numbers, for the Sunday/Monday review card on Home:
 * training days and sets, runs, habit ticks, nutrition days, the weight change, points.
 * Days after today are never counted, so a review of the current week covers what has
 * actually happened.
 */
@Service
public class WeekReviewService {

    private final WorkoutSetRepository sets;
    private final RunService runs;
    private final HabitLogService habitLogs;
    private final ProteinLogRepository proteinLogs;
    private final NutritionSettingsRepository nutritionSettings;
    private final BodyMetricService body;
    private final DailySummaryService summaries;
    private final DayService dayService;
    private final IdentityService identity;

    public WeekReviewService(
            WorkoutSetRepository sets,
            RunService runs,
            HabitLogService habitLogs,
            ProteinLogRepository proteinLogs,
            NutritionSettingsRepository nutritionSettings,
            BodyMetricService body,
            DailySummaryService summaries,
            DayService dayService,
            IdentityService identity) {
        this.sets = sets;
        this.runs = runs;
        this.habitLogs = habitLogs;
        this.proteinLogs = proteinLogs;
        this.nutritionSettings = nutritionSettings;
        this.body = body;
        this.summaries = summaries;
        this.dayService = dayService;
        this.identity = identity;
    }

    public record WeekReview(
            LocalDate weekStart,
            LocalDate weekEnd,
            int trainingDays,
            int sets,
            int runs,
            double runKm,
            long habitTicks,
            int nutritionDaysLogged,
            int daysWithinCalories,
            int daysProteinHit,
            /** Latest weight in the week minus the latest before it; null without both. */
            BigDecimal weightChangeKg,
            int points) {}

    public LocalDate today(UUID userId) {
        return dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
    }

    @Transactional(readOnly = true)
    public WeekReview review(UUID userId, LocalDate anyDayInWeek) {
        LocalDate start = WeeklyVolumeService.mondayOf(anyDayInWeek);
        LocalDate end = start.plusDays(6);
        LocalDate today = today(userId);
        LocalDate to = end.isAfter(today) ? today : end;
        if (to.isBefore(start)) {
            return new WeekReview(start, end, 0, 0, 0, 0, 0, 0, 0, 0, null, 0);
        }

        int trainingDays = sets.findTrainingDays(userId, start, to).size();
        int setCount = sets.findAllForUserBetween(userId, start, to).size();
        int runCount = runs.list(userId, start, to).size();
        double km = Math.round(runs.totalDistanceMeters(userId, start, to) / 100.0) / 10.0;
        long ticks = habitLogs.countDoneBetweenForUser(userId, start, to);

        NutritionSettings targets = nutritionSettings.findById(userId).orElse(null);
        Map<LocalDate, List<ProteinLog>> byDay = proteinLogs.findAllByUserIdAndOccurredOnBetween(userId, start, to).stream()
                .collect(Collectors.groupingBy(ProteinLog::getOccurredOn));
        int within = 0;
        int proteinHit = 0;
        if (targets != null) {
            for (List<ProteinLog> day : byDay.values()) {
                if (day.stream().mapToInt(ProteinLog::getKcal).sum() <= targets.getCalorieBudget()) within++;
                if (day.stream().mapToDouble(ProteinLog::getGrams).sum() >= targets.getProteinTargetG()) proteinHit++;
            }
        }

        // Only a week with its own weigh-in has a change; otherwise "0 kg" would be made up.
        boolean weighedThisWeek = !body.list(userId, start, to).isEmpty();
        BigDecimal latest = weighedThisWeek ? body.latestWeightKg(userId, to) : null;
        BigDecimal before = body.latestWeightKg(userId, start.minusDays(1));
        BigDecimal change = latest != null && before != null ? latest.subtract(before) : null;

        int points = summaries.heatmap(userId, start, to).stream().mapToInt(DailySummary::pointsTotal).sum();

        return new WeekReview(start, end, trainingDays, setCount, runCount, km, ticks, byDay.size(), within, proteinHit, change, points);
    }
}
