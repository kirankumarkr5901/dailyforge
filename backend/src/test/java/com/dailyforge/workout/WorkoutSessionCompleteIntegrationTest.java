package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutPlan;
import com.dailyforge.workout.domain.WorkoutPlanService;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * "All exercises for the day logged" (spec §5.4) — zero points, celebration only, and
 * only meaningful for a session opened against a real plan day.
 */
@SpringBootTest
@Import(WorkoutClockTestConfig.class)
@ActiveProfiles("test")
class WorkoutSessionCompleteIntegrationTest {

    @Autowired private WorkoutPlanService plans;
    @Autowired private ExerciseService exerciseService;
    @Autowired private WorkoutSetService setService;
    @Autowired private com.dailyforge.workout.domain.WorkoutCompletionService completions;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private WorkoutClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private static boolean celebrates(com.dailyforge.points.domain.PointsResult result) {
        return result.celebrations().stream().anyMatch(c -> c.type().name().equals("WORKOUT_COMPLETE"));
    }

    /**
     * The session completes when every planned exercise is marked complete, once.
     * Logging sets never completes it, so the last exercise's later sets do not
     * celebrate again and again.
     */
    @Test
    void theSessionCompletesOnceWhenEveryExerciseIsMarkedComplete() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Push", 1);
        Exercise bench = exerciseService.create(user, "Bench", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);
        Exercise fly = exerciseService.create(user, "Fly", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of(), false);
        plans.addExercise(plan.getId(), user, bench.getId(), 1, null, null, null);
        plans.addExercise(plan.getId(), user, fly.getId(), 1, null, null, null);

        var benchSet = setService.logSet(user, TODAY, bench.getId(), plan.getId(), 1, new BigDecimal("40"), WeightMode.COMBINED, null, 5);
        var flySet = setService.logSet(user, TODAY, fly.getId(), plan.getId(), 1, new BigDecimal("10"), WeightMode.SINGLE, null, 12);
        var flySet2 = setService.logSet(user, TODAY, fly.getId(), plan.getId(), 1, new BigDecimal("10"), WeightMode.SINGLE, null, 12);
        assertThat(celebrates(benchSet.points())).isFalse();
        assertThat(celebrates(flySet.points())).isFalse();
        assertThat(celebrates(flySet2.points())).isFalse();

        UUID sessionId = setService.getOrCreateSession(user, TODAY, plan.getId(), 1).getId();
        assertThat(celebrates(completions.markComplete(sessionId, bench.getId(), user))).isFalse();
        assertThat(celebrates(completions.markComplete(sessionId, fly.getId(), user))).isTrue();

        // Already complete: more sets, or reopening and re-marking, do not celebrate again.
        var later = setService.logSet(user, TODAY, fly.getId(), plan.getId(), 1, new BigDecimal("10"), WeightMode.SINGLE, null, 12);
        assertThat(celebrates(later.points())).isFalse();
        completions.clearComplete(sessionId, fly.getId(), user);
        assertThat(celebrates(completions.markComplete(sessionId, fly.getId(), user))).isFalse();
    }

    /** Auto-complete on the target set can be what finishes the day. */
    @Test
    void reachingTheLastExercisesTargetSetCompletesTheSession() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Push", 1);
        Exercise bench = exerciseService.create(user, "Bench", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);
        plans.addExercise(plan.getId(), user, bench.getId(), 1, 2, 8, null);

        var first = setService.logSet(user, TODAY, bench.getId(), plan.getId(), 1, new BigDecimal("40"), WeightMode.COMBINED, null, 8);
        var second = setService.logSet(user, TODAY, bench.getId(), plan.getId(), 1, new BigDecimal("40"), WeightMode.COMBINED, null, 8);

        assertThat(celebrates(first.points())).isFalse();
        assertThat(celebrates(second.points())).isTrue();
    }

    @Test
    void recentPrIsTheBestSetOfTheLastEarlierDay() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise press = exerciseService.create(user, "Chest press", ExerciseKind.STRENGTH, Equipment.MACHINE, List.of(), false);
        // An older, heavier day, then the last day before today.
        setService.logSet(user, TODAY.minusDays(6), press.getId(), null, null, new BigDecimal("80"), WeightMode.COMBINED, null, 8);
        setService.logSet(user, TODAY.minusDays(3), press.getId(), null, null, new BigDecimal("60"), WeightMode.COMBINED, null, 10);
        setService.logSet(user, TODAY.minusDays(3), press.getId(), null, null, new BigDecimal("65"), WeightMode.COMBINED, null, 6);
        // Today's own sets are not "previous".
        setService.logSet(user, TODAY, press.getId(), null, null, new BigDecimal("90"), WeightMode.COMBINED, null, 5);

        var recent = setService.lastSessionBest(user, press.getId(), TODAY);

        assertThat(recent.totalWeightKg()).isEqualByComparingTo("65");
        assertThat(recent.reps()).isEqualTo(6);
        assertThat(recent.achievedOn()).isEqualTo(TODAY.minusDays(3));
        assertThat(setService.lastSessionBest(user, press.getId(), TODAY.minusDays(6))).isNull();
    }

    @Test
    void loggingEverySetWithoutMarkingDoesNotCompleteTheSession() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Push", 1);
        Exercise bench = exerciseService.create(user, "Bench", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);
        Exercise fly = exerciseService.create(user, "Fly", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of(), false);
        plans.addExercise(plan.getId(), user, bench.getId(), 1, null, null, null);
        plans.addExercise(plan.getId(), user, fly.getId(), 1, null, null, null);

        var afterFirst =
                setService.logSet(user, TODAY, bench.getId(), plan.getId(), 1, new BigDecimal("40"), WeightMode.COMBINED, null, 5);
        boolean completedAfterFirst =
                afterFirst.points().celebrations().stream().anyMatch(c -> c.type().name().equals("WORKOUT_COMPLETE"));
        assertThat(completedAfterFirst).isFalse();

        var afterSecond =
                setService.logSet(user, TODAY, fly.getId(), plan.getId(), 1, new BigDecimal("10"), WeightMode.SINGLE, null, 12);
        boolean completedAfterSecond =
                afterSecond.points().celebrations().stream().anyMatch(c -> c.type().name().equals("WORKOUT_COMPLETE"));
        assertThat(completedAfterSecond).isFalse();
    }

    @Test
    void aFreeformSessionWithNoPlanNeverCompletes() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise bench = exerciseService.create(user, "Bench", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);

        var write = setService.logSet(user, TODAY, bench.getId(), null, null, new BigDecimal("40"), WeightMode.COMBINED, null, 5);

        boolean completed =
                write.points().celebrations().stream().anyMatch(c -> c.type().name().equals("WORKOUT_COMPLETE"));
        assertThat(completed).isFalse();
    }
}
