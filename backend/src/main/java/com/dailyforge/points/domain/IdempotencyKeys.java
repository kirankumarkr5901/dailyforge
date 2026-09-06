package com.dailyforge.points.domain;

import java.util.UUID;

/**
 * Deterministic idempotency keys for awards the engine itself generates, rather than a
 * user action arriving through the frontend's own {@code Idempotency-Key} header (spec
 * §4.5). A reversal or a reconciliation-derived award must produce the exact same key
 * every time it is computed from the same inputs, or re-running a reconciliation pass —
 * which spec §5.3 requires to be safe — would duplicate ledger rows instead of being a
 * no-op the second time.
 */
final class IdempotencyKeys {

    private IdempotencyKeys() {}

    static String forReversal(UUID reversedEntryId) {
        return "reverse:" + reversedEntryId;
    }

    /**
     * {@code generation} distinguishes one award of this (source, rule) slot from the
     * next: the same slot can be reversed and re-awarded many times over the ledger's
     * life (a habit ticked, unticked, and ticked again), and each occasion needs its own
     * key rather than colliding with a row that already exists, permanently, from a
     * previous occasion. The caller passes a count of every entry — reversed or not —
     * that has ever existed for this slot, which is stable for as long as no new entry
     * is written and advances by exactly one each time a fresh award actually happens.
     */
    static String forReconciledAward(String sourceType, UUID sourceId, String ruleCode, long generation) {
        return "reconcile:" + sourceType + ":" + sourceId + ":" + ruleCode + ":" + generation;
    }
}
