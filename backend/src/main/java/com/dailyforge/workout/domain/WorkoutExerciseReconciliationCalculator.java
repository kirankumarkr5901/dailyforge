package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import com.dailyforge.points.domain.RuleConfig;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSessionRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Recomputes one user's one exercise from its raw {@code workout_set} rows: a
 * {@code WORKOUT_SET} entry for every logged set, and a {@code WORKOUT_PR} entry for
 * every set that was a lifetime record when it was done (heavier than every earlier
 * set, or as heavy with more reps). A record keeps its bonus when a later one beats it
 * (owner request: logging a set must never take record points away), so the bonus only
 * moves when a set is deleted or one is back-dated before it. Both rule codes share the {@code WORKOUT_SET} source type, keyed by the
 * set's own real id — no deterministic hashing is needed the way a habit-day pair
 * needed one, since every set already has a row of its own.
 *
 * Recent PR (last 90 days) is a separate, purely displayed value — spec §5.5 defines
 * two PR scopes, but only the lifetime one is named as earning {@code WORKOUT_PR}
 * (spec §5.4), so the recent figure is computed on demand for display, never reconciled.
 */
@Component
public class WorkoutExerciseReconciliationCalculator
        implements ReconciliationCalculator<ReconcileScope.ExercisePr> {

    static final String SOURCE_TYPE = "WORKOUT_SET";

    private final WorkoutSetRepository sets;
    private final WorkoutSessionRepository sessions;
    private final ExerciseRepository exercises;
    private final PointsRuleConfigService ruleConfigs;

    public WorkoutExerciseReconciliationCalculator(
            WorkoutSetRepository sets,
            WorkoutSessionRepository sessions,
            ExerciseRepository exercises,
            PointsRuleConfigService ruleConfigs) {
        this.sets = sets;
        this.sessions = sessions;
        this.exercises = exercises;
        this.ruleConfigs = ruleConfigs;
    }

    @Override
    public Class<ReconcileScope.ExercisePr> scopeType() {
        return ReconcileScope.ExercisePr.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.ExercisePr scope) {
        Exercise exercise = exercises.findById(scope.exerciseId()).orElse(null);
        if (exercise == null) {
            return List.of();
        }

        List<WorkoutSet> live =
                sets.findAllForUserAndExercise(scope.userId(), scope.exerciseId()).stream()
                        .filter(s -> !s.isDeleted())
                        .toList();

        List<DesiredEntry> desired = new ArrayList<>();

        RuleConfig setConfig = ruleConfigs.getSystemDefault("WORKOUT_SET");
        if (setConfig.enabled()) {
            int points = setConfig.getInt("points");
            for (WorkoutSet set : live) {
                desired.add(
                        new DesiredEntry(
                                occurredOnOf(set),
                                PointsCategory.WORKOUT,
                                "WORKOUT_SET",
                                points,
                                SOURCE_TYPE,
                                set.getId(),
                                exercise.getName()));
            }
        }

        // "cardio n/a" (spec §5.4's own PR bonus table) — nothing further to compute.
        if (exercise.getKind() != ExerciseKind.CARDIO) {
            RuleConfig prConfig = ruleConfigs.getSystemDefault("WORKOUT_PR");
            if (prConfig.enabled() && !live.isEmpty()) {
                Map<UUID, java.time.LocalDate> dates = new HashMap<>();
                sessions.findAllById(live.stream().map(WorkoutSet::getSessionId).collect(Collectors.toSet()))
                        .forEach(sess -> dates.put(sess.getId(), sess.getOccurredOn()));
                List<WorkoutSet> inOrder = live.stream()
                        .filter(set -> dates.containsKey(set.getSessionId()))
                        .sorted(Comparator.comparing((WorkoutSet set) -> dates.get(set.getSessionId()))
                                .thenComparing(WorkoutSet::getLoggedAt)
                                .thenComparing(WorkoutSet::getSetNumber))
                        .toList();
                WorkoutSet best = null;
                for (WorkoutSet set : inOrder) {
                    boolean heavier = best == null || set.getTotalWeightKg().compareTo(best.getTotalWeightKg()) > 0;
                    boolean sameWeightMoreReps = best != null
                            && set.getTotalWeightKg().compareTo(best.getTotalWeightKg()) == 0
                            && set.getReps() > best.getReps();
                    if (!heavier && !sameWeightMoreReps) {
                        continue;
                    }
                    int amount = prAmount(prConfig, exercise.getEquipment(), set.getTotalWeightKg(), sameWeightMoreReps);
                    desired.add(new DesiredEntry(
                            dates.get(set.getSessionId()),
                            PointsCategory.WORKOUT,
                            "WORKOUT_PR",
                            amount,
                            SOURCE_TYPE,
                            set.getId(),
                            "Personal record — " + exercise.getName()));
                    best = set;
                }
            }
        }

        return desired;
    }

    private java.time.LocalDate occurredOnOf(WorkoutSet set) {
        return sessions
                .findById(set.getSessionId())
                .orElseThrow(() -> ApiException.notFound("That session"))
                .getOccurredOn();
    }

    private int prAmount(RuleConfig config, Equipment equipment, BigDecimal totalWeightKg, boolean repsOnly) {
        int minAward = config.getInt("minAward");
        int maxAward = config.getInt("maxAward");

        int amount;
        if (repsOnly) {
            amount = config.getInt("repsOnlyBonus");
        } else {
            double typeFactor = config.getNode("typeFactor").get(equipment.name()).asDouble();
            int weightDivisor = config.getInt("weightDivisor");
            amount =
                    (int)
                            Math.round(
                                    typeFactor
                                            * totalWeightKg
                                                    .divide(BigDecimal.valueOf(weightDivisor), 6, RoundingMode.HALF_UP)
                                                    .doubleValue());
        }
        return Math.max(minAward, Math.min(maxAward, amount));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.ExercisePr scope) {
        return sets.findAllForUserAndExercise(scope.userId(), scope.exerciseId()).stream()
                .map(WorkoutSet::getId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
