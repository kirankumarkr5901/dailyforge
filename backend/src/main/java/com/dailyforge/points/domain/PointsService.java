package com.dailyforge.points.domain;

import java.util.UUID;

/**
 * The points engine (spec §5.2). Every module that produces points calls this — nothing
 * else may write to {@code points_entry} (PRODUCT.md invariant 1).
 */
public interface PointsService {

    /** Idempotent: replaying the same command with the same key returns the same result. */
    PointsResult award(AwardCommand cmd);

    /**
     * Reverses every non-reversed entry carrying this source, writing a compensating
     * entry for each rather than deleting anything. Safe to call on a source that has
     * already been reversed, or was never awarded — both are a no-op.
     */
    void reverseBySource(String sourceType, UUID sourceId, String reason);

    /**
     * Recomputes what a scope's entries should be and reconciles the ledger to match
     * (spec §5.3). Returns the net effect of whatever changed — spec §5.2 names this
     * {@code void}, but a habit tick's points and celebrations flow entirely through
     * this method (there is no separate {@code award()} call for a habit module to make),
     * so the caller needs the same delta/newTotal/celebrations shape any other mutation
     * returns (spec §7). A scope with nothing to reconcile still returns a valid result,
     * at zero delta and the account's current total.
     */
    PointsResult reconcile(UUID userId, ReconcileScope scope);

    ScoreSnapshot snapshot(UUID userId, java.time.ZoneId zone);

    /** Rebuilds the cache from {@code SUM(amount)}. Always agrees with the ledger by construction. */
    void recalculate(UUID userId);
}
