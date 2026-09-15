package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.PlanExercise;
import com.dailyforge.workout.domain.WorkoutPlan;
import com.dailyforge.workout.domain.WorkoutPlanService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Plans, days (spec §8.2's cards), and the Extras bucket (day 0). */
@SpringBootTest
@ActiveProfiles("test")
class WorkoutPlanServiceTest {

    @Autowired private WorkoutPlanService plans;
    @Autowired private ExerciseService exercises;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void aUsersFirstPlanBecomesActiveAutomatically() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan first = plans.create(user, "Push Pull Legs", 3);
        assertThat(first.isActive()).isTrue();

        WorkoutPlan second = plans.create(user, "Upper Lower", 2);
        assertThat(second.isActive()).isFalse();
    }

    @Test
    void activatingAPlanDeactivatesEveryOtherOne() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan a = plans.create(user, "A", 2);
        WorkoutPlan b = plans.create(user, "B", 2);

        plans.update(b.getId(), user, null, true);

        assertThat(plans.requireOwned(a.getId(), user).isActive()).isFalse();
        assertThat(plans.requireOwned(b.getId(), user).isActive()).isTrue();
    }

    @Test
    void dayLabelsDefaultToDayNButCanBeRenamed() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Split", 3);

        assertThat(plan.labelFor(1)).isEqualTo("Day 1");

        plans.relabelDay(plan.getId(), user, 1, "Push");
        assertThat(plans.requireOwned(plan.getId(), user).labelFor(1)).isEqualTo("Push");
        assertThat(plans.requireOwned(plan.getId(), user).labelFor(2)).isEqualTo("Day 2"); // untouched
    }

    @Test
    void addingAnExerciseToDayZeroPutsItInTheExtrasBucket() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Split", 2);
        Exercise curl = exercises.create(user, "Curl", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of("arms"), false);

        PlanExercise pe = plans.addExercise(plan.getId(), user, curl.getId(), 0, null, null, null);

        assertThat(pe.getDayIndex()).isZero();
        assertThat(plans.exercisesFor(plan.getId())).extracting(PlanExercise::getDayIndex).containsExactly(0);
    }

    @Test
    void movingAnExerciseFromExtrasToARealDayIsTheSwapAction() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Split", 2);
        Exercise curl = exercises.create(user, "Curl", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of("arms"), false);
        PlanExercise pe = plans.addExercise(plan.getId(), user, curl.getId(), 0, null, null, null);

        PlanExercise moved = plans.moveExercise(plan.getId(), user, pe.getId(), 1);

        assertThat(moved.getDayIndex()).isEqualTo(1);
    }

    @Test
    void anEliteExerciseCanSitOnSeveralDaysAtOnce() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Split", 3);
        Exercise pullUps = exercises.create(user, "Pull-ups", ExerciseKind.STRENGTH, Equipment.BODYWEIGHT, List.of("back"), true);

        plans.addExercise(plan.getId(), user, pullUps.getId(), 1, null, null, null);
        plans.addExercise(plan.getId(), user, pullUps.getId(), 2, null, null, null);

        assertThat(plans.exercisesFor(plan.getId())).hasSize(2);
    }

    @Test
    void reorderingADaySetsSortOrderToMatchTheGivenSequence() {
        UUID user = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(user, "Split", 1);
        Exercise a = exercises.create(user, "A", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);
        Exercise b = exercises.create(user, "B", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of(), false);
        PlanExercise peA = plans.addExercise(plan.getId(), user, a.getId(), 1, null, null, null);
        PlanExercise peB = plans.addExercise(plan.getId(), user, b.getId(), 1, null, null, null);

        plans.reorderDay(plan.getId(), user, 1, List.of(peB.getId(), peA.getId()));

        List<PlanExercise> ordered = plans.exercisesFor(plan.getId());
        assertThat(ordered).extracting(PlanExercise::getId).containsExactly(peB.getId(), peA.getId());
    }

    @Test
    void oneUserCannotReachAnotherUsersPlan() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        WorkoutPlan plan = plans.create(userA, "A's plan", 2);

        assertThatThrownBy(() -> plans.requireOwned(plan.getId(), userB)).isInstanceOf(ApiException.class);
    }
}
