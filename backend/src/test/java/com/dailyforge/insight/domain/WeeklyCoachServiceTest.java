package com.dailyforge.insight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Reading the AI's weekly review. No network. */
class WeeklyCoachServiceTest {

    @Test
    void keepsAtMostThreeWinsOnOneLineEachAndNeverAnEmptySummary() {
        var answer = JsonMapper.builder().build().readTree("""
                {"summary":"","wins":["Four training days","Protein\\nhit 5 days","","3 runs","extra"],"focus":"Sleep by 11."}
                """);
        var review = WeeklyCoachService.parse(LocalDate.of(2026, 11, 23), answer);
        assertThat(review.summary()).isNotBlank();
        assertThat(review.wins()).containsExactly("Four training days", "Protein hit 5 days", "3 runs");
        assertThat(review.focus()).isEqualTo("Sleep by 11.");
    }
}
