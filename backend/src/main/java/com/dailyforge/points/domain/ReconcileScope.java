package com.dailyforge.points.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What part of a user's history a reconciliation pass recomputes (spec §5.3).
 *
 * These five scopes are named in the spec; none has an owning module yet — habits land
 * at M3, workouts at M4, runs at M5, goals at M7. Each module registers a
 * {@link ReconciliationCalculator} for its scope type when it exists. Until then the
 * engine has scopes with no registered calculator, and {@code reconcile()} is a safe
 * no-op for them (see {@code ReconciliationEngine}).
 */
public sealed interface ReconcileScope {

    record Habit(UUID habitId, LocalDate fromDate) implements ReconcileScope {}

    /**
     * {@code userId} is explicit here — unlike {@link Habit}, an exercise is not
     * single-owner: the shared catalog (spec §6, "exercises are shared across plans")
     * means the same {@code exerciseId} can have sets logged by many different users,
     * so the scope cannot be resolved back to one user from {@code exerciseId} alone.
     */
    record ExercisePr(UUID userId, UUID exerciseId) implements ReconcileScope {}

    record DayCommitment(UUID userId, LocalDate date) implements ReconcileScope {}

    record RunPr(UUID userId) implements ReconcileScope {}

    record Goal(UUID goalId) implements ReconcileScope {}
}
