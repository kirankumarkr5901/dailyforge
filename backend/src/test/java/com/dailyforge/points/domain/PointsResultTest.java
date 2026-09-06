package com.dailyforge.points.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code combine()} is what a future module (M3's habit tick, which awards a base point
 * plus, on the right day, a streak bonus) will use to fold several awards from one
 * logical user action into the single response shape spec §7 requires. Untested public
 * behaviour on the module's own result type is worth closing now, before a caller
 * depends on it.
 */
class PointsResultTest {

    private PointsEntry entry(int amount) {
        return PointsEntry.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 3, 12),
                PointsCategory.HABIT,
                "HABIT_BASE",
                amount,
                null,
                null,
                null,
                "test",
                "key-" + UUID.randomUUID());
    }

    @Test
    void combiningSeveralResultsSumsTheDeltaAndConcatenatesEntriesAndCelebrations() {
        PointsResult first =
                new PointsResult(List.of(entry(10)), 10, 10, List.of(Celebration.of(CelebrationType.STREAK)));
        PointsResult second =
                new PointsResult(List.of(entry(20)), 20, 30, List.of(Celebration.of(CelebrationType.ALL_HABITS_DONE)));

        PointsResult combined = PointsResult.combine(List.of(first, second));

        assertThat(combined.delta()).isEqualTo(30);
        assertThat(combined.entries()).hasSize(2);
        assertThat(combined.celebrations())
                .extracting(Celebration::type)
                .containsExactly(CelebrationType.STREAK, CelebrationType.ALL_HABITS_DONE);
    }

    @Test
    void combinedNewTotalIsTheLastResultsBecauseEachAlreadyReflectsWhatCameBeforeIt() {
        PointsResult first = new PointsResult(List.of(entry(10)), 10, 10, List.of());
        PointsResult second = new PointsResult(List.of(entry(20)), 20, 30, List.of());

        assertThat(PointsResult.combine(List.of(first, second)).newTotal()).isEqualTo(30);
    }

    @Test
    void combiningASingleResultIsEquivalentToThatResult() {
        PointsResult only = new PointsResult(List.of(entry(5)), 5, 5, List.of());

        PointsResult combined = PointsResult.combine(List.of(only));

        assertThat(combined.delta()).isEqualTo(5);
        assertThat(combined.newTotal()).isEqualTo(5);
        assertThat(combined.entries()).hasSize(1);
    }

    @Test
    void combiningNothingIsZero() {
        PointsResult combined = PointsResult.combine(List.of());

        assertThat(combined.delta()).isZero();
        assertThat(combined.entries()).isEmpty();
        assertThat(combined.celebrations()).isEmpty();
    }

    @Test
    void theRecordDefensivelyCopiesItsLists() {
        var mutableEntries = new java.util.ArrayList<PointsEntry>();
        mutableEntries.add(entry(1));
        PointsResult result = new PointsResult(mutableEntries, 1, 1, List.of());

        mutableEntries.add(entry(2));

        assertThat(result.entries()).hasSize(1); // unaffected by mutating the caller's list afterward
    }
}
