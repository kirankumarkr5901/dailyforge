package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.ProgressionCalculator;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** "Reps, then weight": go up only once every working set reached the target. */
class ProgressionCalculatorTest {

    private static final LocalDate LAST = LocalDate.of(2026, 11, 20);

    private static List<WorkoutSet> sets(Equipment eq, WeightMode mode, String weight, int... reps) {
        List<WorkoutSet> out = new ArrayList<>();
        for (int i = 0; i < reps.length; i++) {
            BigDecimal w = new BigDecimal(weight);
            out.add(eq == Equipment.BODYWEIGHT
                    ? WorkoutSet.log(UUID.randomUUID(), UUID.randomUUID(), eq, i + 1, null, mode, w, reps[i])
                    : WorkoutSet.log(UUID.randomUUID(), UUID.randomUUID(), eq, i + 1, w, mode, null, reps[i]));
        }
        return out;
    }

    @Test
    void everySetOnTargetSuggestsOneStepUp() {
        var s = ProgressionCalculator.suggest(
                Equipment.BARBELL, 4, 8, LAST, sets(Equipment.BARBELL, WeightMode.COMBINED, "60", 8, 8, 9, 8));

        assertThat(s).isPresent();
        assertThat(s.get().lastWeightKg()).isEqualByComparingTo("60");
        assertThat(s.get().suggestedWeightKg()).isEqualByComparingTo("62.5");
        assertThat(s.get().lastSets()).isEqualTo(4);
        assertThat(s.get().lastReps()).isEqualTo(8);
    }

    @Test
    void oneShortSetMeansStayAtThisWeight() {
        var s = ProgressionCalculator.suggest(
                Equipment.BARBELL, 4, 8, LAST, sets(Equipment.BARBELL, WeightMode.COMBINED, "60", 8, 8, 7, 8));

        assertThat(s).isEmpty();
    }

    @Test
    void fewerSetsThanTheTargetIsNotOnTarget() {
        var s = ProgressionCalculator.suggest(
                Equipment.BARBELL, 4, 8, LAST, sets(Equipment.BARBELL, WeightMode.COMBINED, "60", 8, 8, 8));

        assertThat(s).isEmpty();
    }

    @Test
    void lighterRampUpSetsAreIgnored() {
        List<WorkoutSet> all = new ArrayList<>(sets(Equipment.BARBELL, WeightMode.COMBINED, "40", 5));
        all.addAll(sets(Equipment.BARBELL, WeightMode.COMBINED, "60", 8, 8, 8));

        var s = ProgressionCalculator.suggest(Equipment.BARBELL, 3, 8, LAST, all);

        assertThat(s).isPresent();
        assertThat(s.get().lastWeightKg()).isEqualByComparingTo("60");
    }

    @Test
    void oneDumbbellPerHandStepsEachDumbbell() {
        // 2 x 20 kg dumbbells: the total shown is 40, and the next step is 22.5 each = 45.
        var s = ProgressionCalculator.suggest(
                Equipment.DUMBBELL, 3, 10, LAST, sets(Equipment.DUMBBELL, WeightMode.SINGLE, "20", 10, 10, 10));

        assertThat(s).isPresent();
        assertThat(s.get().lastWeightKg()).isEqualByComparingTo("40");
        assertThat(s.get().suggestedWeightKg()).isEqualByComparingTo("45");
    }

    @Test
    void bodyweightWithNoAddedWeightSuggestsAddingSome() {
        var s = ProgressionCalculator.suggest(
                Equipment.BODYWEIGHT, 4, 8, LAST, sets(Equipment.BODYWEIGHT, WeightMode.COMBINED, "0", 8, 8, 8, 8));

        assertThat(s).isPresent();
        assertThat(s.get().suggestedWeightKg()).isEqualByComparingTo("2.5");
    }

    @Test
    void noTargetRepsMeansNoHint() {
        var s = ProgressionCalculator.suggest(
                Equipment.BARBELL, 4, null, LAST, sets(Equipment.BARBELL, WeightMode.COMBINED, "60", 8, 8, 8, 8));

        assertThat(s).isEmpty();
    }

    @Test
    void nothingLoggedLastTimeMeansNoHint() {
        assertThat(ProgressionCalculator.suggest(Equipment.MACHINE, 3, 10, LAST, List.of())).isEmpty();
    }
}
