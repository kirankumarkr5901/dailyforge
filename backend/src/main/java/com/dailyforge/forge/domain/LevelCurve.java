package com.dailyforge.forge.domain;

/**
 * XP → level. Going from level {@code n} to {@code n + 1} costs
 * {@code round(base * n^exponent)} XP, so early levels come quickly and later ones take
 * real weeks. Level 1 is where everyone starts, at zero XP.
 */
public final class LevelCurve {

    private LevelCurve() {}

    /**
     * @param xpIntoLevel    XP earned since reaching {@code level}
     * @param xpForNextLevel XP this level costs in total — the progress bar's full width
     */
    public record Level(int level, int xpIntoLevel, int xpForNextLevel) {}

    public static int costOf(int level, int base, double exponent) {
        return (int) Math.max(1, Math.round(base * Math.pow(level, exponent)));
    }

    public static Level levelFor(int totalXp, int base, double exponent) {
        int level = 1;
        int remaining = Math.max(0, totalXp);
        int cost = costOf(level, base, exponent);
        while (remaining >= cost) {
            remaining -= cost;
            level++;
            cost = costOf(level, base, exponent);
        }
        return new Level(level, remaining, cost);
    }
}
