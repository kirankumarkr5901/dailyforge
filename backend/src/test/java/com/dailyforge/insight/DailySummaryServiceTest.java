package com.dailyforge.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.insight.domain.DailySummary;
import com.dailyforge.insight.domain.DailySummaryService;
import com.dailyforge.insight.domain.DayState;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The heatmap state machine (spec §8.1.1). {@code app_user.created_at} is a real-clock
 * audit timestamp (like every other entity's, spec's own established pattern), so
 * every date used here is anchored to the real "today" rather than a fake one — the
 * account itself cannot have started in a fake past.
 */
@SpringBootTest
@Import(InsightClockTestConfig.class)
@ActiveProfiles("test")
class DailySummaryServiceTest {

    @Autowired private DailySummaryService summaries;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private InsightClockTestConfig.MutableClock clock;

    private static final LocalDate REAL_TODAY = LocalDate.now(ZoneOffset.UTC);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private void award(UUID user, LocalDate date, PointsCategory category, String key) {
        points.award(
                AwardCommand.withoutSource(user, date, category, "TEST_" + category, 10, "test", key + ":" + UUID.randomUUID()));
    }

    @Test
    void aDayWithBothWorkoutAndRunIsBoth() {
        setToday(REAL_TODAY);
        UUID user = TestUsers.create(users, settings);
        award(user, REAL_TODAY, PointsCategory.WORKOUT, "w");
        award(user, REAL_TODAY, PointsCategory.RUN, "r");

        List<DailySummary> heatmap = summaries.heatmap(user, REAL_TODAY, REAL_TODAY);
        assertThat(heatmap).hasSize(1);
        assertThat(heatmap.get(0).state()).isEqualTo(DayState.BOTH);
        assertThat(heatmap.get(0).pointsTotal()).isEqualTo(20);
    }

    @Test
    void restBecomesMissedOnTheThirdConsecutiveInactiveDay() {
        setToday(REAL_TODAY.plusDays(3));
        UUID user = TestUsers.create(users, settings);
        award(user, REAL_TODAY, PointsCategory.WORKOUT, "w"); // day 0: active, resets the streak

        List<DailySummary> heatmap = summaries.heatmap(user, REAL_TODAY, REAL_TODAY.plusDays(3));

        assertThat(heatmap.get(0).state()).isEqualTo(DayState.WORKOUT);
        assertThat(heatmap.get(1).state()).isEqualTo(DayState.REST); // 1st inactive day
        assertThat(heatmap.get(1).inactiveRunLength()).isEqualTo(1);
        assertThat(heatmap.get(2).state()).isEqualTo(DayState.REST); // 2nd inactive day
        assertThat(heatmap.get(3).state()).isEqualTo(DayState.MISSED); // 3rd inactive day
        assertThat(heatmap.get(3).inactiveRunLength()).isEqualTo(3);
    }

    @Test
    void aFutureDateIsAlwaysEmptyAndNeverTouchesTheStreak() {
        setToday(REAL_TODAY);
        UUID user = TestUsers.create(users, settings);

        List<DailySummary> heatmap = summaries.heatmap(user, REAL_TODAY, REAL_TODAY.plusDays(2));

        assertThat(heatmap.get(1).state()).isEqualTo(DayState.EMPTY);
        assertThat(heatmap.get(2).state()).isEqualTo(DayState.EMPTY);
    }

    @Test
    void aDateBeforeTheAccountExistedIsEmpty() {
        setToday(REAL_TODAY);
        UUID user = TestUsers.create(users, settings);

        List<DailySummary> heatmap = summaries.heatmap(user, REAL_TODAY.minusDays(5), REAL_TODAY);

        assertThat(heatmap.get(0).state()).isEqualTo(DayState.EMPTY); // 5 days before the account existed
        assertThat(heatmap.get(0).date()).isEqualTo(REAL_TODAY.minusDays(5));
    }

    @Test
    void loggingBreaksTheStreakEvenAfterSeveralMissedDays() {
        setToday(REAL_TODAY.plusDays(4));
        UUID user = TestUsers.create(users, settings);
        award(user, REAL_TODAY, PointsCategory.RUN, "r");
        award(user, REAL_TODAY.plusDays(4), PointsCategory.WORKOUT, "w");

        List<DailySummary> heatmap = summaries.heatmap(user, REAL_TODAY, REAL_TODAY.plusDays(4));

        assertThat(heatmap.get(3).state()).isEqualTo(DayState.MISSED); // day 3: 3rd straight inactive day
        assertThat(heatmap.get(4).state()).isEqualTo(DayState.WORKOUT); // day 4: active again
        assertThat(heatmap.get(4).inactiveRunLength()).isZero();
    }
}
