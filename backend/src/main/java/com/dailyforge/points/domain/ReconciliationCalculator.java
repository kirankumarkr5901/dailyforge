package com.dailyforge.points.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Recomputes what a scope's ledger entries should be, from that scope's raw source data
 * — never from the ledger itself. Reading the ledger to decide what the ledger should
 * contain is the bug this whole mechanism exists to prevent: the ledger is the thing
 * being checked, not the source of truth about what is correct.
 *
 * Implemented by the module that owns a given {@link ReconcileScope} — the habit module
 * implements one for {@code ReconcileScope.Habit} at M3, and so on. Registered with the
 * {@code ReconciliationEngine} as a Spring bean; nothing here depends on any concrete
 * module, so the engine can exist and be proven correct before any calculator does.
 */
public interface ReconciliationCalculator<S extends ReconcileScope> {

    Class<S> scopeType();

    /** Which {@code source_type} this calculator's entries are written under. */
    String sourceType();

    /** Everything that should currently be true for this scope, computed from raw source data. */
    List<DesiredEntry> desiredEntries(S scope);

    /**
     * Every source id this specific invocation is authoritative over — the "universe"
     * the engine diffs {@link #desiredEntries} against, not merely every entry of
     * {@link #sourceType} the user has. Without this, two different habits sharing the
     * source type "HABIT_LOG" (or two different dates sharing "DAY_COMMITMENT") would
     * see each other's entries as "exists but is no longer desired" and reverse them —
     * a real bug this method exists specifically to prevent. Must include every id a
     * date/entity in range could produce, whether or not it currently has a desired
     * entry, so a source that WAS desired and no longer is still gets reversed.
     */
    Set<UUID> sourceIdsInScope(S scope);
}
