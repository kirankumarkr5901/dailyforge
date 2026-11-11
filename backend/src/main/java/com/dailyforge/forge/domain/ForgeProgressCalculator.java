package com.dailyforge.forge.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every XP number in Forge 66, derived from which quests were done on which days.
 *
 * There is no XP ledger to drift from this and no nightly job to settle it: a day that
 * closed while the server was asleep is settled the moment anyone reads it, exactly
 * once, because "settling" is just this walk over the history. Running it twice over
 * the same data gives the same answer by construction.
 *
 * Walked chronologically, program by program, day by day:
 * <ul>
 *   <li>each completed quest adds its XP, and the same XP to its area's stat;</li>
 *   <li>a day with every quest done adds the day bonus and extends the streak, and
 *       reaching a configured streak length adds that one-off bonus;</li>
 *   <li>a closed day left incomplete resets the streak, and in Hard Mode costs
 *       {@code hardModePenaltyXp} per undone quest — never taking the total below zero;</li>
 *   <li>today, while still incomplete, changes nothing yet: it is not over.</li>
 * </ul>
 */
public final class ForgeProgressCalculator {

    private ForgeProgressCalculator() {}

    /** The facts about one quest this walk needs, free of JPA. */
    public record QuestFact(UUID programId, LocalDate date, ForgeArea area, int xp, boolean done) {}

    public enum DayStatus {
        DONE,
        PARTIAL,
        MISSED,
        TODAY,
        UPCOMING
    }

    public record DayResult(LocalDate date, int dayNumber, int done, int total, DayStatus status) {}

    public record Progress(
            int totalXp,
            LevelCurve.Level level,
            int currentStreak,
            int bestStreak,
            Map<ForgeArea, Integer> stats,
            int daysCompleted,
            /** Per program, every day from its start to its end, for the calendar. */
            Map<UUID, List<DayResult>> days) {}

    public static Progress compute(
            List<ForgeProgram> programsOldestFirst, List<QuestFact> quests, LocalDate today, ForgeSettings settings) {

        Map<UUID, Map<LocalDate, List<QuestFact>>> byProgramAndDay = new HashMap<>();
        for (QuestFact q : quests) {
            byProgramAndDay
                    .computeIfAbsent(q.programId(), k -> new HashMap<>())
                    .computeIfAbsent(q.date(), k -> new ArrayList<>())
                    .add(q);
        }

        Map<ForgeArea, Integer> stats = new EnumMap<>(ForgeArea.class);
        for (ForgeArea area : ForgeArea.values()) {
            stats.put(area, 0);
        }

        int total = 0;
        int best = 0;
        int current = 0;
        int daysCompleted = 0;
        Map<UUID, List<DayResult>> days = new HashMap<>();

        for (ForgeProgram program : programsOldestFirst) {
            Map<LocalDate, List<QuestFact>> programDays = byProgramAndDay.getOrDefault(program.getId(), Map.of());
            LocalDate lastCounted = program.lastCountedDay();
            int streak = 0;
            List<DayResult> results = new ArrayList<>();

            int dayNumber = 0;
            for (LocalDate d = program.getStartDate(); !d.isAfter(program.getEndDate()); d = d.plusDays(1)) {
                dayNumber++;
                List<QuestFact> dayQuests = programDays.getOrDefault(d, List.of());
                int done = (int) dayQuests.stream().filter(QuestFact::done).count();
                int count = dayQuests.size();

                results.add(new DayResult(d, dayNumber, done, count, statusOf(d, today, done, count)));

                boolean counted = !d.isAfter(lastCounted) && !d.isAfter(today);
                if (!counted) {
                    // A day past an abandonment still keeps the XP of quests already done
                    // on it — they were real work, and they already paid their points.
                    if (program.getEndedOn() != null && d.equals(program.getEndedOn()) && !d.isAfter(today)) {
                        total += addDoneXp(dayQuests, stats);
                    }
                    continue;
                }

                total += addDoneXp(dayQuests, stats);

                if (count > 0 && done == count) {
                    total += settings.dayCompleteXp();
                    streak++;
                    daysCompleted++;
                    best = Math.max(best, streak);
                    total += settings.streakBonusXp().getOrDefault(streak, 0);
                } else if (d.isBefore(today)) {
                    streak = 0;
                    if (program.getMode() == ForgeMode.HARD) {
                        total = Math.max(0, total - (count - done) * settings.hardModePenaltyXp());
                    }
                }
            }

            days.put(program.getId(), results);
            if (program.isActive()) {
                current = streak;
            }
        }

        LevelCurve.Level level = LevelCurve.levelFor(total, settings.levelBase(), settings.levelExponent());
        return new Progress(total, level, current, best, stats, daysCompleted, days);
    }

    private static int addDoneXp(List<QuestFact> dayQuests, Map<ForgeArea, Integer> stats) {
        int xp = 0;
        for (QuestFact q : dayQuests) {
            if (q.done()) {
                xp += q.xp();
                stats.merge(q.area(), q.xp(), Integer::sum);
            }
        }
        return xp;
    }

    private static DayStatus statusOf(LocalDate d, LocalDate today, int done, int total) {
        if (d.isAfter(today)) {
            return DayStatus.UPCOMING;
        }
        if (d.equals(today)) {
            return total > 0 && done == total ? DayStatus.DONE : DayStatus.TODAY;
        }
        if (total > 0 && done == total) {
            return DayStatus.DONE;
        }
        return done > 0 ? DayStatus.PARTIAL : DayStatus.MISSED;
    }
}
