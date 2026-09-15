package com.dailyforge.habit.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.repo.HabitRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Recomputes one habit's base points, streak bonuses and strict-missed penalties from
 * its raw log (spec §5.3's own worked example is exactly this: a 14-day streak bonus
 * invalidated by unticking day 9). Never reads the ledger to decide what the ledger
 * should contain — everything here comes from {@code habit_log} and the habit's own
 * configuration.
 */
@Component
public class HabitReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.Habit> {

    static final String SOURCE_TYPE = "HABIT_LOG";

    private final HabitRepository habits;
    private final IdentityService identity;
    private final DayService dayService;
    private final PointsRuleConfigService ruleConfigs;
    private final HabitStreakService streakService;

    public HabitReconciliationCalculator(
            HabitRepository habits,
            IdentityService identity,
            DayService dayService,
            PointsRuleConfigService ruleConfigs,
            HabitStreakService streakService) {
        this.habits = habits;
        this.identity = identity;
        this.dayService = dayService;
        this.ruleConfigs = ruleConfigs;
        this.streakService = streakService;
    }

    @Override
    public Class<ReconcileScope.Habit> scopeType() {
        return ReconcileScope.Habit.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.Habit scope) {
        Habit habit = habits.findById(scope.habitId()).orElseThrow(() -> ApiException.notFound("That habit"));

        HabitStreakCalculator.Result result = streakService.compute(habit, scope.fromDate());

        List<DesiredEntry> desired = new ArrayList<>();

        if (ruleConfigs.getSystemDefault("HABIT_BASE").enabled()) {
            for (LocalDate date : result.doneDates()) {
                desired.add(
                        new DesiredEntry(
                                date,
                                PointsCategory.HABIT,
                                "HABIT_BASE",
                                habit.getPoints(),
                                SOURCE_TYPE,
                                HabitSourceIds.forHabitDay(habit.getId(), date),
                                habit.getName()));
            }
        }

        if (ruleConfigs.getSystemDefault("HABIT_CONSISTENCY").enabled()) {
            var config = ruleConfigs.getSystemDefault("HABIT_CONSISTENCY");
            int maxExponent = config.getInt("maxExponent");
            for (HabitStreakCalculator.BonusEvent event : result.bonusEvents()) {
                int amount =
                        HabitStreakCalculator.bonusAmount(
                                habit.getBaseBonus(), habit.getBonusMultiplier(), event.multipleOfSeven(), maxExponent);
                desired.add(
                        new DesiredEntry(
                                event.date(),
                                PointsCategory.HABIT,
                                "HABIT_CONSISTENCY",
                                amount,
                                SOURCE_TYPE,
                                HabitSourceIds.forHabitDay(habit.getId(), event.date()),
                                event.multipleOfSeven() * 7 + "-day streak — " + habit.getName()));
            }
        }

        if (habit.getType() == HabitType.STRICT && ruleConfigs.getSystemDefault("HABIT_PENALTY").enabled()) {
            for (LocalDate date : result.missedDates()) {
                desired.add(
                        new DesiredEntry(
                                date,
                                PointsCategory.HABIT,
                                "HABIT_PENALTY",
                                -habit.getPenaltyPoints(),
                                SOURCE_TYPE,
                                HabitSourceIds.forHabitDay(habit.getId(), date),
                                "Missed — " + habit.getName()));
            }
        }

        return desired;
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.Habit scope) {
        Habit habit = habits.findById(scope.habitId()).orElseThrow(() -> ApiException.notFound("That habit"));

        var zone = dayService.zoneOf(identity.requireSettings(habit.getUserId()).getTimeZone());
        LocalDate today = dayService.today(zone);
        LocalDate from = scope.fromDate().isAfter(habit.getActiveFrom()) ? scope.fromDate() : habit.getActiveFrom();

        // Every scheduled date in the walked range, independent of how the streak walk
        // classifies it — including today, even if today is still pending: if a stale
        // entry exists for a date that no longer produces one (the user unticked today
        // after already earning its points), that date still has to be in scope for the
        // reversal to happen.
        Set<UUID> ids = new java.util.HashSet<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            if (ScheduleDays.isScheduled(habit.getScheduleDays(), date)) {
                ids.add(HabitSourceIds.forHabitDay(habit.getId(), date));
            }
        }
        return ids;
    }
}
