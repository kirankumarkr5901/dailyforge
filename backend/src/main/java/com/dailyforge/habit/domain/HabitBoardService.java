package com.dailyforge.habit.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.habit.repo.HabitStreakRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsRuleConfigService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read model behind {@code GET /habits/board?date=} — one call, everything the
 * tracker needs (spec §10: no N+1 waterfalls). The bonus hint strip is computed here,
 * server-side, per spec §8.4: the frontend never decides what is about to be earned.
 */
@Service
public class HabitBoardService {

    private final HabitService habits;
    private final HabitLogRepository logs;
    private final HabitStreakRepository streaks;
    private final HabitLogService habitLogService;
    private final IdentityService identity;
    private final DayService dayService;
    private final PointsRuleConfigService ruleConfigs;

    public HabitBoardService(
            HabitService habits,
            HabitLogRepository logs,
            HabitStreakRepository streaks,
            HabitLogService habitLogService,
            IdentityService identity,
            DayService dayService,
            PointsRuleConfigService ruleConfigs) {
        this.habits = habits;
        this.logs = logs;
        this.streaks = streaks;
        this.habitLogService = habitLogService;
        this.identity = identity;
        this.dayService = dayService;
        this.ruleConfigs = ruleConfigs;
    }

    @Transactional(readOnly = true)
    public HabitBoard board(UUID userId, LocalDate date) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        List<Habit> active =
                habits.listActive(userId).stream().filter(habit -> habit.isScheduledOn(date)).toList();

        List<HabitBoardEntry> entries = new ArrayList<>();
        List<HintCandidate> hintCandidates = new ArrayList<>();
        int maxExponent = ruleConfigs.getSystemDefault("HABIT_CONSISTENCY").getInt("maxExponent");

        for (Habit habit : active) {
            var log = logs.findByHabitIdAndOccurredOn(habit.getId(), date);
            boolean done = log.map(HabitLog::getState).map(s -> s == HabitLogState.DONE).orElse(false);

            HabitDayState state = stateFor(date, today, done);
            var streak = streaks.findById(habit.getId());
            int currentStreak = streak.map(HabitStreak::getCurrentStreak).orElse(0);
            int bestStreak = streak.map(HabitStreak::getBestStreak).orElse(0);

            entries.add(
                    new HabitBoardEntry(
                            habit.getId(),
                            habit.getName(),
                            habit.getIcon(),
                            habit.getPoints(),
                            habit.getType(),
                            state,
                            state == HabitDayState.PLANNED && done,
                            currentStreak,
                            bestStreak,
                            habitLogService.isEditable(habit, date)));

            // Only today's un-ticked habits are candidates for the hint — it tells you
            // what ticking *right now* would achieve, not a hypothetical for another day.
            if (date.isEqual(today) && !done) {
                int reachedStreak = currentStreak + 1;
                if (reachedStreak % 7 == 0) {
                    int n = reachedStreak / 7;
                    int amount =
                            HabitStreakCalculator.bonusAmount(habit.getBaseBonus(), habit.getBonusMultiplier(), n, maxExponent);
                    hintCandidates.add(new HintCandidate(habit.getName(), reachedStreak, amount));
                }
            }
        }

        return new HabitBoard(entries, buildHint(hintCandidates));
    }

    private HabitDayState stateFor(LocalDate date, LocalDate today, boolean done) {
        if (date.isAfter(today)) {
            return HabitDayState.PLANNED;
        }
        if (done) {
            return HabitDayState.DONE;
        }
        return date.isEqual(today) ? HabitDayState.PENDING : HabitDayState.MISSED;
    }

    private record HintCandidate(String habitName, int reachedStreak, int amount) {}

    /** Groups habits that would reach the same streak length at the same bonus, per spec's own example. */
    private String buildHint(List<HintCandidate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        Map<String, List<HintCandidate>> grouped =
                candidates.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        c -> c.reachedStreak() + ":" + c.amount(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<String> sentences = new ArrayList<>();
        for (List<HintCandidate> group : grouped.values()) {
            String names = String.join(" and ", group.stream().map(HintCandidate::habitName).toList());
            int reached = group.get(0).reachedStreak();
            int amount = group.get(0).amount();
            sentences.add(
                    "Ticking "
                            + names
                            + " today reaches a "
                            + reached
                            + "-day streak: +"
                            + amount
                            + (group.size() > 1 ? " each" : ""));
        }
        return String.join("; ", sentences) + ".";
    }
}
