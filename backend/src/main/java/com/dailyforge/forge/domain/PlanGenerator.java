package com.dailyforge.forge.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Turns a questionnaire result into 66 days of quests. Pure and deterministic: the same
 * seed, scores and catalogue always produce the same plan, which is what makes it
 * testable without a database.
 *
 * The shape:
 * <ul>
 *   <li>Three phases. Phase {@code p} draws from tiers {@code 1..p}, leaning on tier
 *       {@code p}, so difficulty climbs as the habits settle.</li>
 *   <li>Each phase has one <b>anchor</b> — a quest from the strongest-need area that
 *       repeats every day of that phase. Repetition is how a habit actually forms; a
 *       plan that changes everything daily trains novelty, not consistency.</li>
 *   <li>The other slots are drawn from the focus areas in proportion to how much each
 *       one needs work, avoiding yesterday's picks where the catalogue allows.</li>
 * </ul>
 */
public final class PlanGenerator {

    /** How often a free slot takes a quest at the phase's own tier rather than an easier one. */
    private static final double TOP_TIER_SHARE = 0.7;

    private PlanGenerator() {}

    public record PlannedQuest(LocalDate date, int dayNumber, String templateCode, int xp) {}

    public static List<PlannedQuest> generate(
            long seed,
            LocalDate startDate,
            ForgeSettings settings,
            ForgeMode mode,
            List<ForgeArea> focusAreas,
            Map<ForgeArea, Integer> scores,
            List<ForgeQuestTemplate> catalogue) {

        Random random = new Random(seed);
        List<ForgeQuestTemplate> pool =
                catalogue.stream()
                        .filter(ForgeQuestTemplate::isActive)
                        .filter(t -> focusAreas.contains(t.getArea()))
                        .sorted(Comparator.comparing(ForgeQuestTemplate::getCode))
                        .toList();

        double xpMultiplier = mode == ForgeMode.HARD ? settings.hardModeXpMultiplier() : 1.0;
        List<PlannedQuest> plan = new ArrayList<>();

        ForgeQuestTemplate anchor = null;
        int anchorPhase = 0;
        Set<String> yesterday = Set.of();

        for (int day = 1; day <= settings.lengthDays(); day++) {
            int phase = settings.phaseOf(day);
            if (phase != anchorPhase) {
                anchor = pickAnchor(pool, focusAreas, phase, random);
                anchorPhase = phase;
            }

            List<ForgeQuestTemplate> today = new ArrayList<>();
            if (anchor != null) {
                today.add(anchor);
            }

            int wanted = settings.questsOnDay(day);
            while (today.size() < wanted) {
                ForgeQuestTemplate next = pickFreeSlot(pool, focusAreas, scores, phase, today, yesterday, random);
                if (next == null) {
                    break; // the catalogue has nothing left for this day — a short day, never a crash
                }
                today.add(next);
            }

            LocalDate date = startDate.plusDays(day - 1L);
            Set<String> codes = new HashSet<>();
            for (ForgeQuestTemplate t : today) {
                plan.add(new PlannedQuest(date, day, t.getCode(), (int) Math.round(t.getBaseXp() * xpMultiplier)));
                codes.add(t.getCode());
            }
            yesterday = codes;
        }
        return plan;
    }

    private static ForgeQuestTemplate pickAnchor(
            List<ForgeQuestTemplate> pool, List<ForgeArea> focusAreas, int phase, Random random) {
        for (ForgeArea area : focusAreas) {
            // The phase's own tier if the area has one, otherwise the hardest it has below it.
            for (int tier = phase; tier >= 1; tier--) {
                int t = tier;
                List<ForgeQuestTemplate> candidates =
                        pool.stream().filter(q -> q.getArea() == area && q.getTier() == t).toList();
                if (!candidates.isEmpty()) {
                    return candidates.get(random.nextInt(candidates.size()));
                }
            }
        }
        return null;
    }

    private static ForgeQuestTemplate pickFreeSlot(
            List<ForgeQuestTemplate> pool,
            List<ForgeArea> focusAreas,
            Map<ForgeArea, Integer> scores,
            int phase,
            List<ForgeQuestTemplate> alreadyToday,
            Set<String> yesterday,
            Random random) {

        List<ForgeQuestTemplate> open =
                pool.stream().filter(t -> t.getTier() <= phase).filter(t -> !alreadyToday.contains(t)).toList();
        if (open.isEmpty()) {
            return null;
        }

        // Weighted by need: an area scored 8 gets picked roughly three times as often as
        // one scored 2. The +1 keeps a focus area that scored zero in the rotation.
        List<ForgeArea> areasWithQuests =
                focusAreas.stream().filter(a -> open.stream().anyMatch(t -> t.getArea() == a)).toList();
        int totalWeight = areasWithQuests.stream().mapToInt(a -> scores.getOrDefault(a, 0) + 1).sum();
        int roll = random.nextInt(totalWeight);
        ForgeArea area = areasWithQuests.get(areasWithQuests.size() - 1);
        for (ForgeArea a : areasWithQuests) {
            roll -= scores.getOrDefault(a, 0) + 1;
            if (roll < 0) {
                area = a;
                break;
            }
        }

        ForgeArea chosenArea = area;
        List<ForgeQuestTemplate> inArea = open.stream().filter(t -> t.getArea() == chosenArea).toList();

        boolean wantTopTier = random.nextDouble() < TOP_TIER_SHARE;
        List<ForgeQuestTemplate> byTier =
                inArea.stream().filter(t -> (t.getTier() == phase) == wantTopTier).toList();
        List<ForgeQuestTemplate> candidates = byTier.isEmpty() ? inArea : byTier;

        List<ForgeQuestTemplate> fresh = candidates.stream().filter(t -> !yesterday.contains(t.getCode())).toList();
        if (!fresh.isEmpty()) {
            candidates = fresh;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
