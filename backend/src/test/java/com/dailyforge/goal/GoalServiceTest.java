package com.dailyforge.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.goal.domain.Goal;
import com.dailyforge.goal.domain.GoalKind;
import com.dailyforge.goal.domain.GoalPeriodType;
import com.dailyforge.goal.domain.GoalRolloverParticipant;
import com.dailyforge.goal.domain.GoalService;
import com.dailyforge.goal.domain.GoalStatus;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Goals (spec §5.4, §8.6). Progress is recomputed from each kind's own module on every
 * read, and a goal auto-completes — awarding GOAL_COMPLETE — the moment that recompute
 * crosses the target, exactly the way the habit and PR reconcilers cross theirs.
 */
@SpringBootTest
@Import(GoalClockTestConfig.class)
@ActiveProfiles("test")
class GoalServiceTest {

    @Autowired private GoalService goalService;
    @Autowired private GoalRolloverParticipant rolloverParticipant;
    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private RunService runService;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private GoalClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void aHabitAdherenceGoalAutoCompletesAndAwardsTheRewardOnceTheTargetIsReached() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        var habit = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        habitLogService.log(habit.getId(), user, TODAY);

        Goal goal =
                goalService.create(
                        user, "Read 1 day", null, GoalKind.HABIT_ADHERENCE, GoalPeriodType.WEEK, TODAY, null, 75, habit.getId(), null, BigDecimal.ONE);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.COMPLETED); // already true at creation-time refresh
        boolean awarded =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "GOAL").stream()
                        .anyMatch(e -> e.getRuleCode().equals("GOAL_COMPLETE") && e.getAmount() == 75);
        assertThat(awarded).isTrue();
    }

    @Test
    void aRunDistanceGoalTracksTheSumOverThePeriod() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        runService.log(user, TODAY, 5_000, 1500, com.dailyforge.run.domain.RunType.TEMPO, null, null);

        Goal goal =
                goalService.create(
                        user, "10 km this week", null, GoalKind.RUN_DISTANCE, GoalPeriodType.WEEK, TODAY, null, 50, null, null, BigDecimal.TEN);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.getCurrentValue()).isEqualByComparingTo("5"); // 5 km logged so far, 10 km target

        runService.log(user, TODAY, 5_000, 1500, com.dailyforge.run.domain.RunType.TEMPO, null, null);
        Goal refreshed = goalService.list(user, null).stream().filter(g -> g.getId().equals(goal.getId())).findFirst().orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(GoalStatus.COMPLETED);
    }

    @Test
    void aCustomGoalIsCompletedManuallyAndNeverAutoCompletes() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Goal goal = goalService.create(user, "Buy new shoes", null, GoalKind.CUSTOM, GoalPeriodType.WEEK, TODAY, null, 30, null, null, null);

        // Listing (the read path that recomputes everything else) must not auto-complete this.
        Goal afterList = goalService.list(user, null).stream().filter(g -> g.getId().equals(goal.getId())).findFirst().orElseThrow();
        assertThat(afterList.getStatus()).isEqualTo(GoalStatus.ACTIVE);

        Goal completed = goalService.complete(goal.getId(), user);
        assertThat(completed.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(entries.sumAmountForUser(user)).isEqualTo(30);
    }

    @Test
    void reopeningACompletedGoalReversesItsAward() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Goal goal = goalService.create(user, "Custom", null, GoalKind.CUSTOM, GoalPeriodType.WEEK, TODAY, null, 40, null, null, null);
        goalService.complete(goal.getId(), user);
        assertThat(entries.sumAmountForUser(user)).isEqualTo(40);

        goalService.reopen(goal.getId(), user);

        assertThat(entries.sumAmountForUser(user)).isZero();
        Goal reloaded = goalService.requireOwned(goal.getId(), user);
        assertThat(reloaded.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void rolloverFailsAnExpiredActiveGoalWithNoPenalty() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        goalService.create(user, "10 km this week", null, GoalKind.RUN_DISTANCE, GoalPeriodType.WEEK, TODAY, null, 50, null, null, BigDecimal.TEN);
        // Never logged any distance — the goal will still be ACTIVE when its week ends.

        LocalDate weekEnd = TODAY.plusDays(6);
        rolloverParticipant.onDayClosed(user, weekEnd);

        Goal reloaded = goalService.list(user, null).stream().findFirst().orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(GoalStatus.FAILED);
        assertThat(entries.sumAmountForUser(user)).isZero(); // no penalty
    }

    @Test
    void oneUserCannotReachAnotherUsersGoal() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        Goal goal = goalService.create(userA, "Custom", null, GoalKind.CUSTOM, GoalPeriodType.WEEK, TODAY, null, 10, null, null, null);

        assertThatThrownBy(() -> goalService.requireOwned(goal.getId(), userB)).isInstanceOf(ApiException.class);
    }
}
