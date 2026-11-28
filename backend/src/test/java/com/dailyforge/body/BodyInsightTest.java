package com.dailyforge.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.dailyforge.body.domain.BodyInsightService;
import com.dailyforge.body.domain.BodyMetric;
import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The body-fat estimate and the body profile. No network. */
@SpringBootTest
@ActiveProfiles("test")
class BodyInsightTest {

    @Autowired private BodyInsightService insights;
    @Autowired private BodyMetricService body;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void theNavyFormulaForMenAndWomen() {
        assertThat(BodyInsightService.navy(BodyInsightService.Sex.MALE, d("175"), d("85"), d("38"), null).doubleValue())
                .isCloseTo(16.9, within(0.2));
        assertThat(BodyInsightService.navy(BodyInsightService.Sex.FEMALE, d("165"), d("75"), d("33"), d("100")).doubleValue())
                .isCloseTo(29.4, within(0.3));
        // Women need hips; a waist not above the neck makes no sense.
        assertThat(BodyInsightService.navy(BodyInsightService.Sex.FEMALE, d("165"), d("75"), d("33"), null)).isNull();
        assertThat(BodyInsightService.navy(BodyInsightService.Sex.MALE, d("175"), d("38"), d("38"), null)).isNull();
    }

    @Test
    void estimatesAppearOnceTheFormulaIsChosen() {
        UUID user = TestUsers.create(users, settings);
        LocalDate day = LocalDate.of(2026, 11, 20);
        body.upsert(user, day, d("80"), d("175"), null, null,
                new BodyMetric.Measurements(d("85"), null, null, null, null, d("38")));
        assertThat(insights.bodyFat(user, day.minusDays(1), day.plusDays(1))).isEmpty();

        insights.saveProfile(user, BodyInsightService.Sex.MALE, BodyInsightService.Goal.LOSE_FAT);
        var points = insights.bodyFat(user, day.minusDays(1), day.plusDays(1));
        assertThat(points).hasSize(1);
        assertThat(points.get(0).pct().doubleValue()).isCloseTo(16.9, within(0.2));
        assertThat(insights.profile(user).goal()).isEqualTo(BodyInsightService.Goal.LOSE_FAT);
    }
}
