package com.dailyforge.points.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A cache, never the source of truth (spec §5.1). The total is always {@code SUM(amount)}
 * over the ledger; this row exists so reading a score does not mean scanning every entry
 * a user has ever earned. {@link com.dailyforge.points.api.PointsAdminController}
 * exposes a way to rebuild it exactly, and that rebuild must always agree with this
 * cache — a property the tests hold onto rather than assume.
 *
 * Concurrent awards to the same user are made safe with a pessimistic write lock taken
 * by the repository (see {@code UserScoreCacheRepository.lockForUpdate}), not by a
 * version column: a racing award should wait and apply cleanly, not fail and force the
 * caller to retry a write the user already believes succeeded.
 */
@Entity
@Table(name = "user_score_cache")
public class UserScoreCache {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @Column(name = "entry_count", nullable = false)
    private int entryCount = 0;

    @Column(name = "recalculated_at", nullable = false)
    private Instant recalculatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserScoreCache() {
        // for JPA
    }

    public static UserScoreCache empty(UUID userId) {
        UserScoreCache cache = new UserScoreCache();
        cache.userId = userId;
        Instant now = Instant.now();
        cache.recalculatedAt = now;
        cache.updatedAt = now;
        return cache;
    }

    /** Applied within the same transaction as the ledger write it corresponds to. */
    public void apply(int delta) {
        this.totalPoints += delta;
        this.entryCount += 1;
        this.updatedAt = Instant.now();
    }

    public void rebuild(int totalPoints, int entryCount) {
        this.totalPoints = totalPoints;
        this.entryCount = entryCount;
        Instant now = Instant.now();
        this.recalculatedAt = now;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public Instant getRecalculatedAt() {
        return recalculatedAt;
    }
}
