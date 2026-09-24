package com.dailyforge.points.domain;

import java.util.Map;

/**
 * What Home needs in one read (spec §5.2, §10 — "must render from a single call, no
 * N+1 waterfalls"). {@code byCategory} covers the month window, matching the segmented
 * breakdown spec §8.1 describes; {@code byCategoryToday}/{@code byCategoryWeek} are the
 * same breakdown for the other two periods, so Home can show whichever one the visitor
 * actually clicked (owner feedback) without a second round trip.
 */
public record ScoreSnapshot(
        int total,
        int today,
        int thisWeek,
        int thisMonth,
        Map<PointsCategory, Integer> byCategory,
        Map<PointsCategory, Integer> byCategoryToday,
        Map<PointsCategory, Integer> byCategoryWeek) {

    public ScoreSnapshot {
        byCategory = Map.copyOf(byCategory);
        byCategoryToday = Map.copyOf(byCategoryToday);
        byCategoryWeek = Map.copyOf(byCategoryWeek);
    }
}
