package com.dailyforge.habit.domain;

import com.dailyforge.habit.repo.HabitRepository;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.RolloverParticipant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Habits' hook into the daily rollover job (spec §5.6). Every one of the job's habit
 * steps — settle future logs that are now current, apply strict penalties, break
 * streaks, evaluate the commitment bonus — already happens automatically the moment
 * {@link HabitReconciliationCalculator} is asked to reconcile a date that has just
 * become {@code < today}, so this participant's only job is to ask it to: re-reconcile
 * every active habit, and the closed day's commitment.
 *
 * Archived habits are skipped deliberately: they cannot be logged any more (see
 * {@code HabitLogService.requireWritable}), so their history is already settled and
 * re-reconciling them on every rollover tick would only be wasted work.
 *
 * Reconciling full history on every rollover, for every habit, is the simple, obviously
 * correct choice — and a real cost once a habit has years of history. Narrowing the
 * window is a legitimate optimisation but reintroduces the risk a full recompute
 * exists to avoid, so it is left for the load-testing pass at M9 rather than built here
 * against a scale this app does not have yet.
 */
@Component
public class HabitRolloverParticipant implements RolloverParticipant {

    private final HabitRepository habits;
    private final PointsService points;
    private final HabitStreakService streakService;

    public HabitRolloverParticipant(HabitRepository habits, PointsService points, HabitStreakService streakService) {
        this.habits = habits;
        this.points = points;
        this.streakService = streakService;
    }

    @Override
    public void onDayClosed(UUID userId, LocalDate closedDate) {
        List<Habit> active = habits.findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(userId);

        for (Habit habit : active) {
            points.reconcile(userId, new ReconcileScope.Habit(habit.getId(), habit.getActiveFrom()));
            streakService.rebuildCache(habit);
        }

        // Evaluated once for the whole day, after every habit's own state has settled
        // above — commitment depends on all of them agreeing that day is complete.
        if (!active.isEmpty()) {
            points.reconcile(userId, new ReconcileScope.DayCommitment(userId, closedDate));
        }
    }
}
