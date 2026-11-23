package com.dailyforge.workout.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * "Reps, then weight": once every working set reached the target reps last time, the
 * next session goes up by one small step. Pure arithmetic over the previous session's
 * sets, so the rule can be tested without a database.
 *
 * <p>The working weight is the heaviest weight used last time. Lighter sets (ramp-ups,
 * drop sets) are ignored. The target only counts as hit when enough sets were done at
 * that weight and every one of them reached the target reps.
 */
public final class ProgressionCalculator {

    /** The usual smallest jump, per bar or per dumbbell. */
    public static final BigDecimal STEP_KG = new BigDecimal("2.5");

    private ProgressionCalculator() {}

    /** What the last session looked like and what to try next. */
    public record Suggestion(LocalDate lastDate, BigDecimal lastWeightKg, int lastSets, int lastReps, BigDecimal suggestedWeightKg) {}

    public static Optional<Suggestion> suggest(
            Equipment equipment, Integer targetSets, Integer targetReps, LocalDate lastDate, List<WorkoutSet> lastSets) {
        if (targetReps == null || targetReps <= 0 || equipment == Equipment.NONE || lastDate == null || lastSets.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal working =
                lastSets.stream().map(WorkoutSet::getTotalWeightKg).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        // A bodyweight set's total is only the added weight, so zero is a real working
        // weight there. For anything loaded, zero means nothing was recorded.
        if (working.signum() == 0 && equipment != Equipment.BODYWEIGHT) {
            return Optional.empty();
        }
        List<WorkoutSet> workingSets =
                lastSets.stream().filter(s -> s.getTotalWeightKg().compareTo(working) == 0).toList();
        int needed = targetSets == null || targetSets <= 0 ? 1 : targetSets;
        if (workingSets.size() < needed || workingSets.stream().anyMatch(s -> s.getReps() < targetReps)) {
            return Optional.empty();
        }
        int minReps = workingSets.stream().mapToInt(WorkoutSet::getReps).min().orElse(0);
        // Totals are what the board shows. A one-per-hand dumbbell set's total is both
        // dumbbells, so one step on each is two steps on the total.
        boolean perHand = equipment != Equipment.BODYWEIGHT
                && workingSets.stream().allMatch(s -> s.getWeightMode() == WeightMode.SINGLE);
        BigDecimal step = perHand ? STEP_KG.multiply(BigDecimal.valueOf(2)) : STEP_KG;
        return Optional.of(new Suggestion(lastDate, working, workingSets.size(), minReps, working.add(step)));
    }
}
