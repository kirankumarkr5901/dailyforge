package com.dailyforge.body.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.body.repo.BodyMetricRepository;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weight logging (spec §8.8). No points anywhere here — every value on this page is
 * computed on read (BMI, band, deltas, moving average, streak), never stored, so the
 * formula can change without a migration.
 */
@Service
public class BodyMetricService {

    private final BodyMetricRepository metrics;
    private final IdentityService identity;
    private final DayService dayService;

    public BodyMetricService(BodyMetricRepository metrics, IdentityService identity, DayService dayService) {
        this.metrics = metrics;
        this.identity = identity;
        this.dayService = dayService;
    }

    public record MovingAveragePoint(LocalDate date, BigDecimal average) {}

    public record Summary(
            List<BodyMetric> entries,
            BigDecimal currentWeightKg,
            BigDecimal bmi,
            BmiBand band,
            BigDecimal deltaSinceLastLog,
            BigDecimal deltaSince30Days,
            List<MovingAveragePoint> movingAverage,
            int weeklyStreak,
            Trend trend) {}

    /**
     * The 7-day average now, and how far it moved over the last two weeks. Daily
     * weigh-ins swing with water and food, so the average is the number worth judging.
     * {@code change14Days} is null until there is an average from about two weeks back
     * to compare with; {@code stalled} is true when it moved less than {@link #STALL_KG}.
     */
    public record Trend(BigDecimal weeklyAverageKg, BigDecimal change14Days, boolean stalled) {}

    /** Less movement than this over 14 days counts as "hasn't moved". */
    public static final BigDecimal STALL_KG = new BigDecimal("0.2");

    /**
     * Compares the latest average with the one closest to 14 days before it. An average
     * from 14 to 21 days earlier counts. Anything older is too far back to call it a
     * two-week change, so there is no comparison then.
     */
    public static Trend trendOf(List<MovingAveragePoint> movingAverage) {
        if (movingAverage.isEmpty()) {
            return new Trend(null, null, false);
        }
        MovingAveragePoint latest = movingAverage.get(movingAverage.size() - 1);
        LocalDate mark = latest.date().minusDays(14);
        MovingAveragePoint earlier = movingAverage.stream()
                .filter(p -> !p.date().isAfter(mark) && !p.date().isBefore(mark.minusDays(7)))
                .reduce((first, second) -> second)
                .orElse(null);
        if (earlier == null) {
            return new Trend(latest.average(), null, false);
        }
        BigDecimal change = latest.average().subtract(earlier.average());
        return new Trend(latest.average(), change, change.abs().compareTo(STALL_KG) < 0);
    }

    /** One row per user per date (spec has no PATCH endpoint) — a second log for the same date updates it in place. */
    @Transactional
    public BodyMetric upsert(UUID userId, LocalDate date, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bodyFatPct, String note) {
        BodyMetric metric =
                metrics.findByUserIdAndOccurredOn(userId, date)
                        .map(
                                existing -> {
                                    existing.apply(weightKg, heightCm, bodyFatPct, note);
                                    return existing;
                                })
                        .orElseGet(() -> BodyMetric.create(userId, date, weightKg, heightCm, bodyFatPct, note));
        return metrics.save(metric);
    }

    /** As {@link #upsert}, plus tape measurements (null ones keep what the day had). */
    @Transactional
    public BodyMetric upsert(
            UUID userId,
            LocalDate date,
            BigDecimal weightKg,
            BigDecimal heightCm,
            BigDecimal bodyFatPct,
            String note,
            BodyMetric.Measurements measurements) {
        BodyMetric metric = upsert(userId, date, weightKg, heightCm, bodyFatPct, note);
        metric.applyMeasurements(measurements);
        return metrics.save(metric);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        metrics.delete(requireOwned(id, userId));
    }

    public BodyMetric requireOwned(UUID id, UUID userId) {
        return metrics.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That log"));
    }

    @Transactional(readOnly = true)
    public List<BodyMetric> list(UUID userId, LocalDate from, LocalDate to) {
        return from != null && to != null
                ? metrics.findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(userId, from, to)
                : metrics.findAllByUserIdOrderByOccurredOnDesc(userId);
    }

    /** A body-metric goal's progress (spec §8.6: "75 kg") — the most recent weight at or before a date. */
    @Transactional(readOnly = true)
    public BigDecimal latestWeightKg(UUID userId, LocalDate onOrBefore) {
        return metrics
                .findFirstByUserIdAndOccurredOnLessThanEqualOrderByOccurredOnDesc(userId, onOrBefore)
                .map(BodyMetric::getWeightKg)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveHeightCm(UUID userId, BodyMetric metric) {
        if (metric != null && metric.getHeightCm() != null) {
            return metric.getHeightCm();
        }
        return identity.requireSettings(userId).getHeightCm();
    }

    public BigDecimal bmiOf(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    public BmiBand bandOf(BigDecimal bmi) {
        if (bmi == null) {
            return null;
        }
        if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0) {
            return BmiBand.UNDERWEIGHT;
        }
        if (bmi.compareTo(BigDecimal.valueOf(25)) < 0) {
            return BmiBand.NORMAL;
        }
        if (bmi.compareTo(BigDecimal.valueOf(30)) < 0) {
            return BmiBand.OVERWEIGHT;
        }
        return BmiBand.OBESE;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);
        List<BodyMetric> recent = metrics.findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(userId, today.minusDays(89), today);

        if (recent.isEmpty()) {
            return new Summary(List.of(), null, null, null, null, null, List.of(), 0, trendOf(List.of()));
        }

        BodyMetric latest = recent.get(recent.size() - 1);
        BigDecimal heightCm = resolveHeightCm(userId, latest);
        BigDecimal bmi = bmiOf(latest.getWeightKg(), heightCm);
        BmiBand band = bandOf(bmi);

        BigDecimal deltaSinceLastLog =
                recent.size() >= 2 ? latest.getWeightKg().subtract(recent.get(recent.size() - 2).getWeightKg()) : null;

        BodyMetric thirtyDaysAgo =
                recent.stream().filter(m -> !m.getOccurredOn().isAfter(today.minusDays(30))).reduce((first, second) -> second).orElse(null);
        BigDecimal deltaSince30Days = thirtyDaysAgo != null ? latest.getWeightKg().subtract(thirtyDaysAgo.getWeightKg()) : null;

        List<MovingAveragePoint> movingAverage = new ArrayList<>();
        for (BodyMetric point : recent) {
            List<BodyMetric> window =
                    recent.stream()
                            .filter(m -> !m.getOccurredOn().isBefore(point.getOccurredOn().minusDays(6)) && !m.getOccurredOn().isAfter(point.getOccurredOn()))
                            .toList();
            BigDecimal sum = window.stream().map(BodyMetric::getWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = sum.divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
            movingAverage.add(new MovingAveragePoint(point.getOccurredOn(), average));
        }

        int weeklyStreak = weeklyStreak(userId, today);

        return new Summary(
                recent, latest.getWeightKg(), bmi, band, deltaSinceLastLog, deltaSince30Days, movingAverage, weeklyStreak, trendOf(movingAverage));
    }

    /** Consecutive ISO weeks, ending at the current one, with at least one log (spec §8.8 [ADD]). No points attached. */
    private int weeklyStreak(UUID userId, LocalDate today) {
        int streak = 0;
        LocalDate weekCursor = today;
        for (int weeksChecked = 0; weeksChecked < 520; weeksChecked++) { // 10 years is plenty of a safety cap
            LocalDate weekStart = weekCursor.minusDays(weekCursor.getDayOfWeek().getValue() - 1); // Monday of that week
            LocalDate weekEnd = weekStart.plusDays(6);
            boolean hasLog = !metrics.findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(userId, weekStart, weekEnd).isEmpty();
            if (!hasLog) {
                break;
            }
            streak++;
            weekCursor = weekStart.minusDays(1);
        }
        return streak;
    }
}
