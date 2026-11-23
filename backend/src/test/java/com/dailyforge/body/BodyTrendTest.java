package com.dailyforge.body;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.body.domain.BodyMetricService.MovingAveragePoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The weekly-average trend: is the scale actually moving over two weeks? */
class BodyTrendTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static MovingAveragePoint p(int daysAgo, String avg) {
        return new MovingAveragePoint(TODAY.minusDays(daysAgo), new BigDecimal(avg));
    }

    @Test
    void aRealDropIsNotAStall() {
        var t = BodyMetricService.trendOf(List.of(p(15, "80.00"), p(7, "79.60"), p(0, "79.20")));

        assertThat(t.weeklyAverageKg()).isEqualByComparingTo("79.20");
        assertThat(t.change14Days()).isEqualByComparingTo("-0.80");
        assertThat(t.stalled()).isFalse();
    }

    @Test
    void underTwoHundredGramsInTwoWeeksIsAStall() {
        var t = BodyMetricService.trendOf(List.of(p(14, "80.00"), p(0, "79.90")));

        assertThat(t.stalled()).isTrue();
    }

    @Test
    void theAverageClosestToTwoWeeksBackIsUsed() {
        var t = BodyMetricService.trendOf(List.of(p(20, "82.00"), p(16, "80.00"), p(0, "79.95")));

        assertThat(t.change14Days()).isEqualByComparingTo("-0.05");
        assertThat(t.stalled()).isTrue();
    }

    @Test
    void withoutHistoryFromTwoWeeksBackThereIsNoVerdict() {
        var t = BodyMetricService.trendOf(List.of(p(10, "80.00"), p(0, "80.00")));

        assertThat(t.change14Days()).isNull();
        assertThat(t.stalled()).isFalse();
    }

    @Test
    void historyTooFarBackDoesNotCount() {
        var t = BodyMetricService.trendOf(List.of(p(30, "80.00"), p(0, "80.00")));

        assertThat(t.change14Days()).isNull();
        assertThat(t.stalled()).isFalse();
    }

    @Test
    void noLogsMeansNoTrend() {
        var t = BodyMetricService.trendOf(List.of());

        assertThat(t.weeklyAverageKg()).isNull();
        assertThat(t.stalled()).isFalse();
    }
}
