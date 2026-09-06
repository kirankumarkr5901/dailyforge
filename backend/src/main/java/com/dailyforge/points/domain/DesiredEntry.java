package com.dailyforge.points.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One line a {@link ReconciliationCalculator} says *should* exist in the ledger, given
 * the current state of its source data — step 1 of spec §5.3's recompute-and-reconcile
 * pass. The engine diffs a list of these against what is actually in the ledger and
 * writes only the difference.
 *
 * {@code (sourceType, sourceId, ruleCode)} is the natural key the diff matches on: one
 * source row can justify more than one rule code (a habit log justifies both
 * {@code HABIT_BASE} and, on the right day, {@code HABIT_CONSISTENCY}), and each such
 * pairing is tracked as its own line.
 */
public record DesiredEntry(
        LocalDate occurredOn,
        PointsCategory category,
        String ruleCode,
        int amount,
        String sourceType,
        UUID sourceId,
        String description) {}
