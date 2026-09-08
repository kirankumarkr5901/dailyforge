package com.dailyforge.points.domain;

import java.util.Map;

/**
 * What Home needs in one read (spec §5.2, §10 — "must render from a single call, no
 * N+1 waterfalls"). {@code byCategory} covers the month window, matching the segmented
 * breakdown spec §8.1 describes.
 */
public record ScoreSnapshot(
        int total, int today, int thisWeek, int thisMonth, Map<PointsCategory, Integer> byCategory) {

    public ScoreSnapshot {
        byCategory = Map.copyOf(byCategory);
    }
}
