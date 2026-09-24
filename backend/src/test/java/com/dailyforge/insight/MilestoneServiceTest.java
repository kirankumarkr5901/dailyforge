package com.dailyforge.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.goal.domain.GoalKind;
import com.dailyforge.goal.domain.GoalPeriodType;
import com.dailyforge.goal.domain.GoalService;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.insight.domain.MilestoneService;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.run.domain.RunType;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The automatic recap half of "milestones for every month, yearly" (owner feedback) —
 * a period's totals assembled from what each tracker already logged, nothing tracked
 * separately for this. {@code app_user.created_at} is a real-clock audit timestamp
 * (the same constraint {@link DailySummaryServiceTest} works around), so every date
 * here is anchored to the real "today" rather than a fake one.
 */
@SpringBootTest
@Import(InsightClockTestConfig.class)
@ActiveProfiles("test")
class MilestoneServiceTest {

    @Autowired private MilestoneService milestones;
    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private RunService runService;
    @Autowired private GoalService goalService;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private InsightClockTestConfig.MutableClock clock;

    private static final LocalDate REAL_TODAY = LocalDate.now(ZoneOffset.UTC);
    // The first of the current real month — every seeded date in the "whole month" test
    // sits inside [monthStart, REAL_TODAY] so nothing depends on the month having more
    // days left to run.
    private static final LocalDate MONTH_START = REAL_TODAY.withDayOfMonth(1);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void aMonthlyRecapTotalsWhatEachTrackerLoggedThatMonth() {
        // A habit/run tick is only writable "today or yesterday" (spec §4.3), so each is
        // logged with the clock actually sitting on that day, mirroring how a user would
        // have logged it in the moment rather than backdating from the future.
        setToday(MONTH_START);
        UUID user = TestUsers.create(users, settings);

        var habit =
                habitService.create(
                        user, "Read", "book", 10, HabitType.NORMAL, 0, 0, BigDecimal.ONE, 127, MONTH_START);
        habitLogService.log(habit.getId(), user, MONTH_START);

        // A day's own hasRun/hasWorkout/points-by-category readback goes through the
        // heatmap (DailySummaryService), which — like every other insight query — only
        // ever walks from the account's own (real-clock) creation date forward; a date
        // seeded before that is invisible to it even though the run/habit module's own
        // raw tables still hold the row (that's what keeps habitsCompleted/runDistance
        // correct above). So the entries this recap reads *through the heatmap* are
        // seeded on "today" rather than earlier in the month.
        setToday(REAL_TODAY);
        habitLogService.log(habit.getId(), user, REAL_TODAY);
        runService.log(user, REAL_TODAY, 5_000, 1500, RunType.TEMPO, null, null);

        points.award(
                AwardCommand.withoutSource(
                        user, REAL_TODAY, PointsCategory.WORKOUT, "TEST_WORKOUT", 20, "test", "wk:" + UUID.randomUUID()));

        var goal =
                goalService.create(
                        user, "Ship a feature", null, GoalKind.CUSTOM, GoalPeriodType.MONTH, MONTH_START, null, 50, null, null, null);
        goalService.complete(goal.getId(), user);

        var recap = milestones.recap(user, RecapPeriod.MONTH, REAL_TODAY);

        assertThat(recap.startDate()).isEqualTo(MONTH_START);
        assertThat(recap.habitsCompleted()).isEqualTo(2);
        assertThat(recap.runDays()).isEqualTo(1);
        assertThat(recap.runDistanceMeters()).isEqualTo(5_000);
        assertThat(recap.workoutDays()).isEqualTo(1);
        assertThat(recap.goalsCompleted()).isEqualTo(1);
        assertThat(recap.byCategory().get(PointsCategory.GOAL)).isEqualTo(50);
    }

    @Test
    void aRecapForTheCurrentMonthNeverWalksPastToday() {
        setToday(REAL_TODAY);
        UUID user = TestUsers.create(users, settings);

        var recap = milestones.recap(user, RecapPeriod.MONTH, REAL_TODAY);

        assertThat(recap.endDate()).isEqualTo(REAL_TODAY);
        assertThat(recap.endDate()).isBeforeOrEqualTo(REAL_TODAY.with(TemporalAdjusters.lastDayOfMonth()));
    }

    @Test
    void aYearlyRecapSpansTheWholeCalendarYearClampedToToday() {
        setToday(REAL_TODAY);
        UUID user = TestUsers.create(users, settings);

        var recap = milestones.recap(user, RecapPeriod.YEAR, REAL_TODAY);

        assertThat(recap.startDate()).isEqualTo(REAL_TODAY.with(TemporalAdjusters.firstDayOfYear()));
        assertThat(recap.endDate()).isEqualTo(REAL_TODAY);
    }
}
