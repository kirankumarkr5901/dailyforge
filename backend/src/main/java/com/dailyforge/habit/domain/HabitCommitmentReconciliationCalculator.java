package com.dailyforge.habit.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.habit.repo.HabitRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "Every active habit scheduled for that day is complete" (spec §5.4), evaluated across
 * the whole day rather than per habit — which is why this is a separate calculator
 * against {@link ReconcileScope.DayCommitment} instead of living inside
 * {@link HabitReconciliationCalculator}. Ticking any one habit can tip the day complete
 * or take it back, so both scopes are reconciled together on every log change.
 */
@Component
public class HabitCommitmentReconciliationCalculator
        implements ReconciliationCalculator<ReconcileScope.DayCommitment> {

    static final String SOURCE_TYPE = "DAY_COMMITMENT";

    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final IdentityService identity;
    private final DayService dayService;
    private final PointsRuleConfigService ruleConfigs;

    public HabitCommitmentReconciliationCalculator(
            HabitRepository habits,
            HabitLogRepository logs,
            IdentityService identity,
            DayService dayService,
            PointsRuleConfigService ruleConfigs) {
        this.habits = habits;
        this.logs = logs;
        this.identity = identity;
        this.dayService = dayService;
        this.ruleConfigs = ruleConfigs;
    }

    @Override
    public Class<ReconcileScope.DayCommitment> scopeType() {
        return ReconcileScope.DayCommitment.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.DayCommitment scope) {
        var config = ruleConfigs.getSystemDefault("HABIT_COMMITMENT");
        if (!config.enabled()) {
            return List.of();
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(scope.userId()).getTimeZone());
        LocalDate today = dayService.today(zone);
        if (scope.date().isAfter(today)) {
            return List.of(); // cannot assess a day that has not arrived yet
        }

        List<Habit> scheduled =
                habits.findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(scope.userId()).stream()
                        .filter(habit -> habit.isScheduledOn(scope.date()))
                        .toList();

        // Nothing scheduled means nothing to complete — vacuous truth is not a reward.
        if (scheduled.isEmpty()) {
            return List.of();
        }

        List<java.util.UUID> habitIds = scheduled.stream().map(Habit::getId).toList();
        Set<java.util.UUID> doneHabitIds =
                logs.findAllByHabitIdInAndOccurredOn(habitIds, scope.date()).stream()
                        .filter(log -> log.getState() == HabitLogState.DONE)
                        .map(HabitLog::getHabitId)
                        .collect(java.util.stream.Collectors.toSet());

        boolean allDone = doneHabitIds.containsAll(habitIds);
        if (!allDone) {
            return List.of();
        }

        int amount = identity.requireSettings(scope.userId()).getCommitmentBonus();
        return List.of(
                new DesiredEntry(
                        scope.date(),
                        PointsCategory.HABIT,
                        "HABIT_COMMITMENT",
                        amount,
                        SOURCE_TYPE,
                        HabitSourceIds.forDayCommitment(scope.userId(), scope.date()),
                        "All habits complete"));
    }

    @Override
    public Set<java.util.UUID> sourceIdsInScope(ReconcileScope.DayCommitment scope) {
        // Exactly one date is ever in play per call — this is what keeps one day's
        // commitment bonus from being seen as "no longer desired" and reversed every
        // time a DIFFERENT day's commitment is reconciled, even though both entries
        // share the DAY_COMMITMENT source type.
        return Set.of(HabitSourceIds.forDayCommitment(scope.userId(), scope.date()));
    }
}
