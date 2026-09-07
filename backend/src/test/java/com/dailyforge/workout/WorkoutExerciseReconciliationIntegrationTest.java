package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutSet;
import com.dailyforge.workout.domain.WorkoutSetService;
import com.dailyforge.workout.repo.ExerciseRepository;
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
 * Spec §5.3's reconciliation mechanism, applied to the workout PR slot instead of a
 * habit's streak: logging a heavier set moves the record and reverses the old holder's
 * bonus; deleting the current record-holder falls back to whoever is next best.
 */
@SpringBootTest
@Import(WorkoutClockTestConfig.class)
@ActiveProfiles("test")
class WorkoutExerciseReconciliationIntegrationTest {

    @Autowired private ExerciseService exerciseService;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutSetService setService;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private WorkoutClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void loggingAHeavierSetOvertakesTheOldRecordAndReversesItsBonus() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise benchPress = exerciseService.create(user, "Bench press", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("chest"), false);

        setService.logSet(user, TODAY, benchPress.getId(), null, null, new BigDecimal("50"), WeightMode.COMBINED, null, 5);
        // 1 (WORKOUT_SET) + round(1.2 * (50/5)) = 1 + 12 = 13
        assertThat(entries.sumAmountForUser(user)).isEqualTo(13);

        setService.logSet(user, TODAY, benchPress.getId(), null, null, new BigDecimal("60"), WeightMode.COMBINED, null, 5);
        // +1 (new set) -12 (old PR reversed) +round(1.2*(60/5))=14 (new PR) = +3 -> running total 16
        assertThat(entries.sumAmountForUser(user)).isEqualTo(16);

        long activePrEntries =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "WORKOUT_SET").stream()
                        .filter(e -> e.getRuleCode().equals("WORKOUT_PR"))
                        .count();
        assertThat(activePrEntries).isEqualTo(1); // only the 60kg set holds it now
    }

    @Test
    void aRepsOnlyImprovementAtTheSameWeightPaysTheSmallerFlatBonus() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise squat = exerciseService.create(user, "Squat", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("legs"), false);

        setService.logSet(user, TODAY, squat.getId(), null, null, new BigDecimal("60"), WeightMode.COMBINED, null, 5);
        int afterFirst = entries.sumAmountForUser(user); // 1 + 14 = 15

        setService.logSet(user, TODAY, squat.getId(), null, null, new BigDecimal("60"), WeightMode.COMBINED, null, 8);
        // +1 (new set) -14 (old PR reversed) +5 (reps-only bonus) = -8
        assertThat(entries.sumAmountForUser(user)).isEqualTo(afterFirst - 8);
    }

    @Test
    void deletingTheCurrentRecordFallsBackToTheNextBestSet() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise deadlift = exerciseService.create(user, "Deadlift", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("back"), false);

        setService.logSet(user, TODAY, deadlift.getId(), null, null, new BigDecimal("100"), WeightMode.COMBINED, null, 5);
        var write =
                setService.logSet(user, TODAY, deadlift.getId(), null, null, new BigDecimal("120"), WeightMode.COMBINED, null, 5);
        WorkoutSet heavier = write.set();

        setService.deleteSet(user, heavier.getId());

        // Falls back to the 100kg set: round(1.2 * (100/5)) = 24, plus its own WORKOUT_SET point.
        var activePr =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "WORKOUT_SET").stream()
                        .filter(e -> e.getRuleCode().equals("WORKOUT_PR"))
                        .findFirst()
                        .orElseThrow();
        assertThat(activePr.getAmount()).isEqualTo(24);
    }

    @Test
    void bodyweightExercisesRankByAddedWeightOnly() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise pullUp = exerciseService.create(user, "Pull-up", ExerciseKind.STRENGTH, Equipment.BODYWEIGHT, List.of("back"), false);

        // enteredWeight is irrelevant for a bodyweight exercise — only addedWeight counts.
        setService.logSet(user, TODAY, pullUp.getId(), null, null, new BigDecimal("999"), WeightMode.COMBINED, new BigDecimal("10"), 12);

        var pr =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "WORKOUT_SET").stream()
                        .filter(e -> e.getRuleCode().equals("WORKOUT_PR"))
                        .findFirst()
                        .orElseThrow();
        // round(1.0 * (10/5)) = 2
        assertThat(pr.getAmount()).isEqualTo(2);
    }

    @Test
    void cardioExercisesNeverEarnAPrBonusOnlyTheSetPoint() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise rowing = exerciseService.create(user, "Rowing", ExerciseKind.CARDIO, Equipment.NONE, List.of(), false);

        setService.logSet(user, TODAY, rowing.getId(), null, null, BigDecimal.ZERO, WeightMode.COMBINED, null, 1);

        assertThat(entries.sumAmountForUser(user)).isEqualTo(1); // WORKOUT_SET only
        boolean anyPr =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "WORKOUT_SET").stream()
                        .anyMatch(e -> e.getRuleCode().equals("WORKOUT_PR"));
        assertThat(anyPr).isFalse();
    }

    @Test
    void loggingOutsideTheEditWindowIsRejected() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Exercise curl = exerciseService.create(user, "Curl", ExerciseKind.STRENGTH, Equipment.DUMBBELL, List.of("arms"), false);

        assertThatThrownBy(
                        () ->
                                setService.logSet(
                                        user, TODAY.minusDays(30), curl.getId(), null, null, new BigDecimal("10"), WeightMode.SINGLE, null, 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void oneUsersSetsNeverAffectAnotherUsersRecordEvenOnTheSameSharedCatalogExercise() {
        setToday(TODAY);
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        // owner null: a real shared-catalog exercise, visible to and loggable by anyone —
        // this is exactly the case ReconcileScope.ExercisePr needed a userId for.
        Exercise shared =
                exerciseRepository.save(
                        Exercise.create(null, "Overhead press", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("shoulders"), false));

        setService.logSet(userA, TODAY, shared.getId(), null, null, new BigDecimal("40"), WeightMode.COMBINED, null, 5);
        setService.logSet(userB, TODAY, shared.getId(), null, null, new BigDecimal("100"), WeightMode.COMBINED, null, 5);

        // B's heavier set must not steal A's PR entry, nor vice versa — each user's own
        // best set on this shared exercise id holds their own, independent record.
        var prA =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(userA, "WORKOUT_SET").stream()
                        .filter(e -> e.getRuleCode().equals("WORKOUT_PR"))
                        .findFirst()
                        .orElseThrow();
        var prB =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(userB, "WORKOUT_SET").stream()
                        .filter(e -> e.getRuleCode().equals("WORKOUT_PR"))
                        .findFirst()
                        .orElseThrow();
        assertThat(prA.getAmount()).isEqualTo(10); // round(1.2 * (40/5))
        assertThat(prB.getAmount()).isEqualTo(24); // round(1.2 * (100/5))
    }
}
