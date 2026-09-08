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

    record ExercisePr(UUID exerciseId) implements ReconcileScope {}

    record DayCommitment(UUID userId, LocalDate date) implements ReconcileScope {}

    record RunPr(UUID userId) implements ReconcileScope {}

    record Goal(UUID goalId) implements ReconcileScope {}
}
