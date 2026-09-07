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
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private WorkoutClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void theCelebrationFiresOnlyOnceEveryPlannedExerciseHasBeenLogged() {
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
        assertThat(completedAfterSecond).isTrue();
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
