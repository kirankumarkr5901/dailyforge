package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.PlanExercise;
import com.dailyforge.workout.domain.WorkoutPlanAiService;
import com.dailyforge.workout.domain.WorkoutPlanService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Saving an AI plan draft: it reuses the user's exercises and only creates the missing ones. */
@SpringBootTest
@Import(WorkoutClockTestConfig.class)
@ActiveProfiles("test")
class WorkoutPlanAiSaveTest {

    @Autowired private WorkoutPlanAiService aiPlans;
    @Autowired private WorkoutPlanService plans;
    @Autowired private ExerciseService exercises;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void aSavedDraftReusesKnownExercisesAndDoesNotBecomeActiveOverAnExistingPlan() {
        UUID user = TestUsers.create(users, settings);
        var current = plans.create(user, "My plan", 2);
        Exercise bench = exercises.create(user, "My Bench", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("chest"), false);

        var draft = new WorkoutPlanAiService.Draft("Upper Lower", "", List.of(
                new WorkoutPlanAiService.DraftDay("Upper", List.of(
                        new WorkoutPlanAiService.DraftExercise(bench.getId(), "My Bench", "chest", Equipment.BARBELL, 3, 6, 10, 120),
                        new WorkoutPlanAiService.DraftExercise(null, "Landmine Press", "shoulders", Equipment.BARBELL, 3, 8, 12, 90))),
                new WorkoutPlanAiService.DraftDay("Lower", List.of(
                        new WorkoutPlanAiService.DraftExercise(null, "Landmine Press", "shoulders", Equipment.BARBELL, 2, 8, 12, 90)))));
        var saved = aiPlans.save(user, draft);

        assertThat(saved.isActive()).isFalse();
        assertThat(plans.requireOwned(current.getId(), user).isActive()).isTrue();
        List<PlanExercise> rows = plans.exercisesFor(saved.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getExerciseId()).isEqualTo(bench.getId());
        // The new exercise is created once and used on both days.
        assertThat(rows.get(1).getExerciseId()).isEqualTo(rows.get(2).getExerciseId());
        assertThat(rows.get(0).getTargetRepsMax()).isEqualTo(10);
        assertThat(rows.get(0).getRestSeconds()).isEqualTo(120);
    }
}
