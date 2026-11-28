package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.MainMuscles;
import com.dailyforge.workout.domain.MuscleTargetService;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Weekly sets per muscle against each muscle's own target, and the points for reaching them. */
@SpringBootTest
@Import(WorkoutClockTestConfig.class)
@ActiveProfiles("test")
class MuscleTargetPointsTest {

    @Autowired private WorkoutSetService setService;
    @Autowired private ExerciseService exercises;
    @Autowired private MuscleTargetService targets;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private WorkoutClockTestConfig.MutableClock clock;

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 9);

    private long count(UUID user, String rule) {
        return entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "MUSCLE_WEEK").stream()
                .filter(e -> e.getRuleCode().equals(rule))
                .count();
    }

    private void log(UUID user, Exercise e, int sets) {
        for (int i = 0; i < sets; i++) {
            setService.logSet(user, MONDAY, e.getId(), null, null, new BigDecimal("40"), WeightMode.COMBINED, null, 10);
        }
    }

    @Test
    void aMuscleReachingItsMinimumPaysOnceAndFallsBackWhenASetIsDeleted() {
        clock.set(MONDAY.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
        UUID user = TestUsers.create(users, settings);
        targets.set(user, "biceps", 3, 6);
        Exercise curl = exercises.create(user, "Curl", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of("arms"), false);

        log(user, curl, 2);
        assertThat(count(user, "MUSCLE_TARGET")).isZero();
        var third = setService.logSet(user, MONDAY, curl.getId(), null, null, new BigDecimal("40"), WeightMode.COMBINED, null, 10);
        assertThat(count(user, "MUSCLE_TARGET")).isEqualTo(1); // "arms" counts toward biceps
        log(user, curl, 2);
        assertThat(count(user, "MUSCLE_TARGET")).isEqualTo(1); // once per week

        setService.deleteSet(user, third.set().getId());
        setService.deleteSet(user, setService.logSet(user, MONDAY, curl.getId(), null, null, new BigDecimal("40"), WeightMode.COMBINED, null, 10).set().getId());
        assertThat(count(user, "MUSCLE_TARGET")).isEqualTo(1); // still 4 sets
    }

    @Test
    void everyMainMuscleReachingItPaysTheBonus() {
        clock.set(MONDAY.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
        UUID user = TestUsers.create(users, settings);
        for (String m : MainMuscles.ALL) {
            targets.set(user, m, 1, 3);
        }
        for (String m : MainMuscles.ALL) {
            log(user, exercises.create(user, "Ex " + m, ExerciseKind.STRENGTH, Equipment.MACHINE, List.of(m), false), 1);
        }
        assertThat(count(user, "MUSCLE_TARGET")).isEqualTo(MainMuscles.ALL.size());
        assertThat(count(user, "MUSCLE_TARGET_ALL")).isEqualTo(1);
    }
}
