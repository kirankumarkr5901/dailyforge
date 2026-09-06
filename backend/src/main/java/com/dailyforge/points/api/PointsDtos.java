package com.dailyforge.points.api;

import com.dailyforge.points.domain.Celebration;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsEntry;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.ScoreSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PointsDtos {

    private PointsDtos() {}

    public record LedgerEntryResponse(
            UUID id,
            LocalDate occurredOn,
            PointsCategory category,
            String ruleCode,
            int amount,
            String description,
            boolean reversed,
            boolean isReversal,
            Instant createdAt) {

        public static LedgerEntryResponse of(PointsEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(),
                    entry.getOccurredOn(),
                    entry.getCategory(),
                    entry.getRuleCode(),
                    entry.getAmount(),
                    entry.getDescription(),
                    entry.isReversed(),
                    entry.isReversal(),
                    entry.getCreatedAt());
        }
    }

    /**
     * The shape spec §7 requires every mutating endpoint to embed: the caller learns
     * what changed and what to celebrate in the same round trip.
     */
    public record PointsEnvelope(int delta, int newTotal, List<CelebrationResponse> celebrations) {

        public static PointsEnvelope of(PointsResult result) {
            return new PointsEnvelope(
                    result.delta(), result.newTotal(), result.celebrations().stream().map(CelebrationResponse::of).toList());
        }
    }

    public record CelebrationResponse(String type, Map<String, Object> details) {
        public static CelebrationResponse of(Celebration celebration) {
            return new CelebrationResponse(celebration.type().name(), celebration.details());
        }
    }

    public record SnapshotResponse(
            int total, int today, int thisWeek, int thisMonth, Map<PointsCategory, Integer> byCategory) {
        public static SnapshotResponse of(ScoreSnapshot snapshot) {
            return new SnapshotResponse(
                    snapshot.total(),
                    snapshot.today(),
                    snapshot.thisWeek(),
                    snapshot.thisMonth(),
                    snapshot.byCategory());
        }
    }
}
