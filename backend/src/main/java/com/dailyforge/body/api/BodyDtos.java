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
            String note) {}

    public record MetricResponse(UUID id, LocalDate date, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bodyFatPct, String note) {
        public static MetricResponse of(BodyMetric metric) {
            return new MetricResponse(
                    metric.getId(), metric.getOccurredOn(), metric.getWeightKg(), metric.getHeightCm(), metric.getBodyFatPct(), metric.getNote());
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
            int weeklyStreak) {

        public static SummaryResponse of(Summary summary) {
            return new SummaryResponse(
                    summary.entries().stream().map(MetricResponse::of).toList(),
                    summary.currentWeightKg(),
                    summary.bmi(),
                    summary.band(),
                    summary.deltaSinceLastLog(),
                    summary.deltaSince30Days(),
                    summary.movingAverage().stream().map(MovingAveragePointResponse::of).toList(),
                    summary.weeklyStreak());
        }
    }
}
