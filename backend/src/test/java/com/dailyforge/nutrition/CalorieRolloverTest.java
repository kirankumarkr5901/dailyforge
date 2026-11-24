package com.dailyforge.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.nutrition.domain.CalorieRolloverParticipant;
import com.dailyforge.nutrition.domain.ProteinLog;
import com.dailyforge.nutrition.repo.ProteinLogRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The day-close hook judges the closed day against the calorie budget, once. */
@SpringBootTest
@ActiveProfiles("test")
class CalorieRolloverTest {

    @Autowired private CalorieRolloverParticipant rollover;
    @Autowired private ProteinLogRepository logs;
    @Autowired private PointsEntryRepository ledger;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private int calorieEntries(UUID user) {
        return (int) ledger.findAll().stream()
                .filter(e -> e.getUserId().equals(user) && "CALORIE_BUDGET".equals(e.getRuleCode()))
                .count();
    }

    @Test
    void aClosedDayWithinBudgetIsPaidOnceWhenItCloses() {
        UUID user = TestUsers.create(users, settings);
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        logs.save(ProteinLog.create(user, yesterday, "Lunch", 30, 700, 20));

        rollover.onDayClosed(user, yesterday);
        rollover.onDayClosed(user, yesterday);

        assertThat(calorieEntries(user)).isEqualTo(1);
    }

    @Test
    void aClosedDayWithNothingLoggedPaysNothing() {
        UUID user = TestUsers.create(users, settings);

        rollover.onDayClosed(user, LocalDate.now(ZoneOffset.UTC).minusDays(1));

        assertThat(calorieEntries(user)).isZero();
    }
}
