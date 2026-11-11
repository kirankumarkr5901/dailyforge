package com.dailyforge.forge.domain;

import java.util.List;
import java.util.Map;

/**
 * Every tunable number in Forge 66, read from {@code points_rule_config}
 * ({@code FORGE_PROGRESSION} and {@code FORGE_QUEST}) — nothing here is a literal in code
 * (PRODUCT.md invariant 6).
 *
 * @param questsPerPhase quests per day in each of the three phases
 * @param streakBonusXp  streak length → one-off XP paid the day that length is reached
 * @param pointsPerXp    what a completed quest pays into the main score, per XP it is worth
 */
public record ForgeSettings(
        int lengthDays,
        List<Integer> questsPerPhase,
        int focusAreas,
        int levelBase,
        double levelExponent,
        double hardModeXpMultiplier,
        int dayCompleteXp,
        int hardModePenaltyXp,
        Map<Integer, Integer> streakBonusXp,
        double pointsPerXp) {

    public static final int PHASES = 3;

    public ForgeSettings {
        questsPerPhase = List.copyOf(questsPerPhase);
        streakBonusXp = Map.copyOf(streakBonusXp);
        if (questsPerPhase.size() != PHASES) {
            throw new IllegalStateException("FORGE_PROGRESSION.questsPerPhase must have " + PHASES + " entries");
        }
    }

    /** 1, 2 or 3 — the program split into three equal stretches (22 days each at 66). */
    public int phaseOf(int dayNumber) {
        int phase = 1 + (dayNumber - 1) * PHASES / lengthDays;
        return Math.max(1, Math.min(PHASES, phase));
    }

    public int questsOnDay(int dayNumber) {
        return questsPerPhase.get(phaseOf(dayNumber) - 1);
    }

    /** What a quest worth {@code xp} pays into the main score when completed. */
    public int pointsFor(int xp) {
        return (int) Math.round(xp * pointsPerXp);
    }
}
