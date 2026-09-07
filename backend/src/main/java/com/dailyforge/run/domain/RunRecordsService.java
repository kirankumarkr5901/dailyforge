package com.dailyforge.run.domain;

import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.run.repo.RunRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The PR and bracket views (spec §8.5) — computed on demand rather than a materialized
 * {@code run_record} table, the same call M4 made for a workout exercise's PRs: these
 * are pure display values, not part of the points-invariant chain.
 */
@Service
public class RunRecordsService {

    /** A run counts toward a bracket once its distance is at least this far (spec §8.5). */
    public enum Bracket {
        D5K(5_000),
        D10K(10_000),
        D15K(15_000),
        D21K(21_100),
        D25K(25_000),
        D42K(42_200),
        D50K(50_000);

        private final int minMetres;

        Bracket(int minMetres) {
            this.minMetres = minMetres;
        }

        public int minMetres() {
            return minMetres;
        }
    }

    private final RunRepository runs;
    private final PointsEntryRepository entries;

    public RunRecordsService(RunRepository runs, PointsEntryRepository entries) {
        this.runs = runs;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public List<Run> topByDistance(UUID userId, int limit) {
        return runs.findAllByUserIdAndDeletedAtIsNullOrderByDistanceMetersDescPaceSecPerKmAsc(userId).stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<Run> topByPace(UUID userId, int limit) {
        return runs.findAllByUserIdAndDeletedAtIsNullOrderByPaceSecPerKmAsc(userId).stream().limit(limit).toList();
    }

    /** Fastest pace among runs that reached this bracket's distance — a bracket's "PR" is a time, not a distance. */
    @Transactional(readOnly = true)
    public Map<Bracket, List<Run>> byBracket(UUID userId, int limitPerBracket) {
        Map<Bracket, List<Run>> result = new LinkedHashMap<>();
        for (Bracket bracket : Bracket.values()) {
            result.put(
                    bracket,
                    runs.findAllByUserIdAndDeletedAtIsNullAndDistanceMetersGreaterThanEqualOrderByPaceSecPerKmAsc(userId, bracket.minMetres()).stream()
                            .limit(limitPerBracket)
                            .toList());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public int lifetimeRunPoints(UUID userId) {
        return entries.sumAmountByCategoryAllTime(userId, PointsCategory.RUN);
    }
}
