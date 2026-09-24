package com.dailyforge.habit.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.habit.repo.HabitRepository;
import com.dailyforge.habit.repo.HabitStreakRepository;
import com.dailyforge.points.domain.PointsRuleConfigService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create, edit, reorder, archive. No points logic lives here — that is entirely the
 * reconciler's job, driven off {@code habit_log} — this only shapes the habit's own
 * configuration.
 */
@Service
public class HabitService {

    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final HabitStreakRepository streaks;
    private final PointsRuleConfigService ruleConfigs;

    public HabitService(
            HabitRepository habits,
            HabitLogRepository logs,
            HabitStreakRepository streaks,
            PointsRuleConfigService ruleConfigs) {
        this.habits = habits;
        this.logs = logs;
        this.streaks = streaks;
        this.ruleConfigs = ruleConfigs;
    }

    @Transactional(readOnly = true)
    public List<Habit> listActive(UUID userId) {
        return habits.findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(userId);
    }

    /** Including archived ones — a habit archived last week must not erase last month's
     * recap of what it earned while still active (exposed for the milestone recap). */
    @Transactional(readOnly = true)
    public List<Habit> listAll(UUID userId) {
        return habits.findAllByUserIdOrderBySortOrderAsc(userId);
    }

    @Transactional(readOnly = true)
    public Habit requireOwned(UUID habitId, UUID userId) {
        return habits.findByIdAndUserId(habitId, userId).orElseThrow(() -> ApiException.notFound("That habit"));
    }

    /** Whether the commitment-bonus prompt is even relevant yet ("asked once, at the first habit's creation"). */
    @Transactional(readOnly = true)
    public boolean isFirstHabit(UUID userId) {
        return !habits.existsByUserIdAndArchivedAtIsNull(userId);
    }

    @Transactional
    public Habit create(
            UUID userId,
            String name,
            String icon,
            int points,
            HabitType type,
            int penaltyPoints,
            Integer baseBonus,
            BigDecimal bonusMultiplier,
            int scheduleDays,
            LocalDate today) {

        // Defaults come from configuration (spec §5.4: baseBonus 20, multiplier 1.5),
        // never hardcoded here — a habit that does not override them still traces its
        // starting point back to points_rule_config.
        var consistencyConfig = ruleConfigs.getSystemDefault("HABIT_CONSISTENCY");
        int resolvedBaseBonus = baseBonus != null ? baseBonus : consistencyConfig.getInt("defaultBaseBonus");
        BigDecimal resolvedMultiplier =
                bonusMultiplier != null
                        ? bonusMultiplier
                        : BigDecimal.valueOf(consistencyConfig.getDouble("defaultMultiplier"));

        int nextSort = habits.findAllByUserIdOrderBySortOrderAsc(userId).size();

        Habit habit =
                Habit.create(
                        userId,
                        name,
                        icon,
                        points,
                        type,
                        penaltyPoints,
                        resolvedBaseBonus,
                        resolvedMultiplier,
                        scheduleDays,
                        today,
                        nextSort);
        habits.save(habit);
        streaks.save(HabitStreak.empty(habit.getId()));
        return habit;
    }

    @Transactional
    public Habit update(
            UUID habitId,
            UUID userId,
            String name,
            String icon,
            Integer points,
            HabitType type,
            Integer penaltyPoints,
            Integer scheduleDays) {
        Habit habit = requireOwned(habitId, userId);

        if (name != null) {
            habit.rename(name);
        }
        if (icon != null) {
            habit.updateIcon(icon);
        }
        if (points != null) {
            habit.updatePoints(points);
        }
        if (type != null) {
            habit.updateType(type, penaltyPoints != null ? penaltyPoints : habit.getPenaltyPoints());
        }
        if (scheduleDays != null) {
            habit.updateSchedule(scheduleDays);
        }

        habits.save(habit);
        return habit;
    }

    @Transactional
    public void reorder(UUID userId, List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Habit habit = requireOwned(orderedIds.get(i), userId);
            habit.updateSortOrder(i);
            habits.save(habit);
        }
    }

    /** A habit with logs is archived rather than deleted, so its history and past points survive. */
    @Transactional
    public void deleteOrArchive(UUID habitId, UUID userId) {
        Habit habit = requireOwned(habitId, userId);

        if (logs.existsByHabitId(habit.getId())) {
            habit.archive(Instant.now());
            habits.save(habit);
        } else {
            streaks.deleteById(habit.getId());
            habits.delete(habit);
        }
    }
}
