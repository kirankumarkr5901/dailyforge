package com.dailyforge.body;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.body.domain.BmiBand;
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

/** Weight logging (spec §8.8) — no points, every value computed on read. */
@SpringBootTest
@ActiveProfiles("test")
class BodyMetricServiceTest {

    @Autowired private BodyMetricService metrics;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void loggingTheSameDateTwiceUpdatesInPlaceRatherThanCreatingASecondRow() {
        UUID user = TestUsers.create(users, settings);
        LocalDate date = LocalDate.of(2026, 3, 12);

        metrics.upsert(user, date, new BigDecimal("80.0"), null, null, null);
        metrics.upsert(user, date, new BigDecimal("79.5"), null, null, null);

        assertThat(metrics.list(user, date, date)).hasSize(1);
        assertThat(metrics.list(user, date, date).get(0).getWeightKg()).isEqualByComparingTo("79.5");
    }

    @Test
    void bmiBandsMatchTheStandardWhoThresholds() {
        assertThat(metrics.bandOf(new BigDecimal("18.0"))).isEqualTo(BmiBand.UNDERWEIGHT);
        assertThat(metrics.bandOf(new BigDecimal("22.0"))).isEqualTo(BmiBand.NORMAL);
        assertThat(metrics.bandOf(new BigDecimal("27.0"))).isEqualTo(BmiBand.OVERWEIGHT);
        assertThat(metrics.bandOf(new BigDecimal("31.0"))).isEqualTo(BmiBand.OBESE);
    }

    @Test
    void bmiIsWeightOverHeightSquaredInMetres() {
        // 80 kg at 180 cm -> 80 / 1.8^2 = 24.7
        BigDecimal bmi = metrics.bmiOf(new BigDecimal("80"), new BigDecimal("180"));
        assertThat(bmi).isEqualByComparingTo("24.7");
    }

    @Test
    void deltasCompareAgainstThePreviousLogAndThirtyDaysAgo() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        metrics.upsert(user, today.minusDays(35), new BigDecimal("82.0"), null, null, null);
        metrics.upsert(user, today.minusDays(1), new BigDecimal("81.0"), null, null, null);
        metrics.upsert(user, today, new BigDecimal("80.5"), null, null, null);

        var summary = metrics.summary(user);

        assertThat(summary.deltaSinceLastLog()).isEqualByComparingTo("-0.5"); // 80.5 - 81.0
        assertThat(summary.deltaSince30Days()).isEqualByComparingTo("-1.5"); // 80.5 - 82.0
    }

    @Test
    void aGapInLoggingBreaksTheWeeklyStreak() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        metrics.upsert(user, today, new BigDecimal("75"), null, null, null); // this week only

        var summary = metrics.summary(user);
        assertThat(summary.weeklyStreak()).isEqualTo(1);
    }
}
