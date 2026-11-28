package com.dailyforge.insight.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The trend maths, and reading the AI's projection. No network. */
class FutureYouServiceTest {

    @Test
    void theSlopeIsTheTrendPerDay() {
        // 0.1 kg lost every day.
        var points = List.of(new double[] {0, 72.0}, new double[] {10, 71.0}, new double[] {20, 70.0});
        assertThat(FutureYouService.slopePerDay(points)).isCloseTo(-0.1, within(1e-9));
        assertThat(FutureYouService.slopePerDay(List.of(new double[] {0, 1}))).isZero();
    }

    @Test
    void keepsAtMostFiveLinesAndNeverAnEmptyHeadline() {
        var a = JsonMapper.builder().build().readTree("""
                {"headline":"","lines":["a","b","","c","d","e","f"],"reality":"","lever":"Sleep by 11."}
                """);
        var p = FutureYouService.parse(LocalDate.of(2026, 12, 1), a);
        assertThat(p.headline()).isNotBlank();
        assertThat(p.lines()).containsExactly("a", "b", "c", "d", "e");
        assertThat(p.lever()).isEqualTo("Sleep by 11.");
    }
}
