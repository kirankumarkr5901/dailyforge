package com.dailyforge.body.api;

import com.dailyforge.body.domain.BmiBand;
import com.dailyforge.body.domain.BodyMetric;
import com.dailyforge.body.domain.BodyMetricService.MovingAveragePoint;
import com.dailyforge.body.domain.BodyMetricService.Summary;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BodyDtos {

    private BodyDtos() {}

    public record LogWeightRequest(
            @NotNull LocalDate date,
            @NotNull @DecimalMin("1") BigDecimal weightKg,
            BigDecimal heightCm,
            @DecimalMin("0") @DecimalMax("100") BigDecimal bodyFatPct,
            String note,
            @DecimalMin("20") @DecimalMax("250") BigDecimal waistCm,
            @DecimalMin("20") @DecimalMax("250") BigDecimal chestCm,
            @DecimalMin("10") @DecimalMax("100") BigDecimal armCm,
            @DecimalMin("20") @DecimalMax("250") BigDecimal hipsCm,
            @DecimalMin("10") @DecimalMax("150") BigDecimal thighCm,
            @DecimalMin("15") @DecimalMax("80") BigDecimal neckCm) {}

    public record MetricResponse(
            UUID id,
            LocalDate date,
            BigDecimal weightKg,
            BigDecimal heightCm,
            BigDecimal bodyFatPct,
            String note,
            BigDecimal waistCm,
            BigDecimal chestCm,
            BigDecimal armCm,
            BigDecimal hipsCm,
            BigDecimal thighCm,
            BigDecimal neckCm) {
        public static MetricResponse of(BodyMetric metric) {
            var m = metric.getMeasurements();
            return new MetricResponse(
                    metric.getId(),
                    metric.getOccurredOn(),
                    metric.getWeightKg(),
                    metric.getHeightCm(),
                    metric.getBodyFatPct(),
                    metric.getNote(),
                    m.waistCm(),
                    m.chestCm(),
                    m.armCm(),
                    m.hipsCm(),
                    m.thighCm(),
                    m.neckCm());
        }
    }

    public record MovingAveragePointResponse(LocalDate date, BigDecimal average) {
        public static MovingAveragePointResponse of(MovingAveragePoint point) {
            return new MovingAveragePointResponse(point.date(), point.average());
        }
    }

    public record SummaryResponse(
            List<MetricResponse> entries,
            BigDecimal currentWeightKg,
            BigDecimal bmi,
            BmiBand band,
            BigDecimal deltaSinceLastLog,
            BigDecimal deltaSince30Days,
            List<MovingAveragePointResponse> movingAverage,
            int weeklyStreak,
            /** Latest 7-day average weight. */
            BigDecimal weeklyAverageKg,
            /** How much that average moved over two weeks; null without enough history. */
            BigDecimal weeklyAverageChange14Days,
            /** The average moved less than 0.2 kg in two weeks. */
            boolean stalled) {

        public static SummaryResponse of(Summary summary) {
            return new SummaryResponse(
                    summary.entries().stream().map(MetricResponse::of).toList(),
                    summary.currentWeightKg(),
                    summary.bmi(),
                    summary.band(),
                    summary.deltaSinceLastLog(),
                    summary.deltaSince30Days(),
                    summary.movingAverage().stream().map(MovingAveragePointResponse::of).toList(),
                    summary.weeklyStreak(),
                    summary.trend().weeklyAverageKg(),
                    summary.trend().change14Days(),
                    summary.trend().stalled());
        }
    }
}
