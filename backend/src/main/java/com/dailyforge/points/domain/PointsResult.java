package com.dailyforge.points.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * What an award or reversal produced. This is what crosses the module boundary back to
 * a controller, and its {@code delta}/{@code newTotal}/{@code celebrations} shape is
 * exactly what spec §7 says every mutating endpoint must return — one round trip, no
 * separate call to find out what was earned.
 */
public record PointsResult(
        List<PointsEntry> entries, int delta, int newTotal, List<Celebration> celebrations) {

    public PointsResult {
        entries = List.copyOf(entries);
        celebrations = List.copyOf(celebrations);
    }

    /** Combines several results from one logical action (e.g. a habit tick that also hits a streak). */
    public static PointsResult combine(List<PointsResult> results) {
        List<PointsEntry> entries = new ArrayList<>();
        List<Celebration> celebrations = new ArrayList<>();
        int delta = 0;
        int newTotal = 0;

        for (PointsResult result : results) {
            entries.addAll(result.entries());
            celebrations.addAll(result.celebrations());
            delta += result.delta();
            // The last result's cache read is the freshest total; each result already
            // reflects every award applied before it within the same transaction.
            newTotal = result.newTotal();
        }

        return new PointsResult(entries, delta, newTotal, celebrations);
    }
}
