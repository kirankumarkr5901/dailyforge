package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutBoardService;
import com.dailyforge.workout.domain.WorkoutCompletionService;
import com.dailyforge.workout.domain.WorkoutSession;
import com.dailyforge.workout.domain.WorkoutPlanService;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The board's three states: to do, in progress, finished.
 *
 * The distinction that matters is the one between the last two. Sets logged says work
 * started; only an explicit mark says it finished. Nothing else can stand in for it —
 * three sets is a full job for one lift and a warm-up for another — so these tests pin
 * that the two facts stay independent of each other.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkoutCompletionTest {

    @Autowired private WorkoutCompletionService completions;
    @Autowired private WorkoutBoardService board;
    @Autowired private WorkoutSetService setService;
    @Autowired private ExerciseService exercises;
    @Autowired private WorkoutPlanService plans;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private UUID user;
    private WorkoutSession session;
    private UUID exerciseId;

    /**
     * A planned day, not a freeform session. A freeform session's board only contains
     * exercises that already have sets — there is nothing to *plan* to do — so "to do"
     * is a state that only exists against a plan, which is exactly the case the three
     * states were asked for.
     */
    @BeforeEach
    void setUp() {
        user = TestUsers.create(users, settings);
        exerciseId =
                exercises.create(user, "Bench press " + UUID.randomUUID(), ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("chest"), false)
                        .getId();
        var plan = plans.create(user, "Test plan", 3);
        plans.addExercise(plan.getId(), user, exerciseId, 1, 3, 8, null);
        session = setService.getOrCreateSession(user, TODAY, plan.getId(), 1);
    }

    private WorkoutBoardService.BoardExercise entry() {
        return board.board(user, session).stream()
                .filter(be -> be.exercise().getId().equals(exerciseId))
                .findFirst()
                .orElseThrow();
    }

    private void logASet() {
        setService.logSet(
                user,
                TODAY,
                exerciseId,
                session.getPlanId(),
                session.getDayIndex(),
                new java.math.BigDecimal("60"),
                WeightMode.COMBINED,
                null,
                8);
    }

    @Test
    void anExerciseWithNoSetsAndNoMarkIsStillToDo() {
        var e = entry();
        assertThat(e.sets()).isEmpty();
        assertThat(e.completedAt()).isNull();
    }

    /** The whole point: logging work does not finish it. */
    @Test
    void loggingASetStartsTheExerciseButDoesNotFinishIt() {
        logASet();

        var e = entry();
        assertThat(e.sets()).hasSize(1);
        assertThat(e.completedAt()).isNull();
    }

    @Test
    void markingCompleteRecordsIt() {
        logASet();

        completions.markComplete(session.getId(), exerciseId, user);

        assertThat(entry().completedAt()).isNotNull();
    }

    /**
     * An exercise can be finished without a set logged through the app — you did it, you
     * just did not record the numbers. Refusing the mark would make the board disagree
     * with the gym.
     */
    @Test
    void anExerciseCanBeMarkedCompleteWithoutAnySetsLogged() {
        completions.markComplete(session.getId(), exerciseId, user);

        var e = entry();
        assertThat(e.sets()).isEmpty();
        assertThat(e.completedAt()).isNotNull();
    }

    /** Taps and networks both stutter; pressing twice must not be an error or a second row. */
    @Test
    void markingCompleteTwiceIsHarmless() {
        completions.markComplete(session.getId(), exerciseId, user);
        var first = entry().completedAt();

        completions.markComplete(session.getId(), exerciseId, user);

        assertThat(entry().completedAt()).isEqualTo(first);
    }

    @Test
    void theMarkCanBeTakenBack() {
        completions.markComplete(session.getId(), exerciseId, user);
        completions.clearComplete(session.getId(), exerciseId, user);

        assertThat(entry().completedAt()).isNull();
    }

    /** Logging more sets afterwards does not silently un-finish it. */
    @Test
    void loggingAfterCompletingLeavesItCompleted() {
        completions.markComplete(session.getId(), exerciseId, user);

        logASet();

        var e = entry();
        assertThat(e.sets()).hasSize(1);
        assertThat(e.completedAt()).isNotNull();
    }

    @Test
    void anotherUsersSessionCannotBeMarked() {
        UUID intruder = TestUsers.create(users, settings);

        assertThatThrownBy(() -> completions.markComplete(session.getId(), exerciseId, intruder))
                .isInstanceOf(ApiException.class);

        assertThat(entry().completedAt()).isNull();
    }
}
