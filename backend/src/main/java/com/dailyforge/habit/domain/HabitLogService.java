package com.dailyforge.habit.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticking and unticking. The write here is deliberately small — upsert or delete one
 * {@code habit_log} row — because every point this produces comes from reconciling
 * afterward, in the same transaction, never from computing an amount inline (spec §5.3).
 */
@Service
public class HabitLogService {

    private final HabitLogRepository logs;
    private final HabitService habits;
    private final PointsService points;
    private final HabitStreakService streakService;
    private final DayService dayService;
    private final IdentityService identity;

    public HabitLogService(
            HabitLogRepository logs,
            HabitService habits,
            PointsService points,
            HabitStreakService streakService,
            DayService dayService,
            IdentityService identity) {
        this.logs = logs;
        this.habits = habits;
        this.points = points;
        this.streakService = streakService;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** Ticks a habit for a date. Returns the reconciled points (spec §7: one round trip). */
    @Transactional
    public PointsResult log(UUID habitId, UUID userId, LocalDate date) {
        Habit habit = habits.requireOwned(habitId, userId);
        requireWritable(habit, date);

        HabitLog log =
                logs.findByHabitIdAndOccurredOn(habitId, date)
                        .orElseGet(() -> HabitLog.create(habitId, date, HabitLogState.DONE));
        log.setState(HabitLogState.DONE);
        logs.save(log);

        return reconcileAfterChange(habit, userId, date);
    }

    /** Unticks — deletes the raw log rather than merely marking it, so the day returns to "not logged". */
    @Transactional
    public PointsResult unlog(UUID habitId, UUID userId, LocalDate date) {
        Habit habit = habits.requireOwned(habitId, userId);
        requireWritable(habit, date);

        logs.findByHabitIdAndOccurredOn(habitId, date).ifPresent(logs::delete);

        return reconcileAfterChange(habit, userId, date);
    }

    /**
     * Both scopes, every time: ticking one habit can both change its own streak and tip
     * the whole day's commitment bonus, so both are reconciled together, in one
     * transaction, regardless of which habit changed. The two results are combined into
     * the single envelope spec §7 expects from one round trip.
     */
    private PointsResult reconcileAfterChange(Habit habit, UUID userId, LocalDate date) {
        PointsResult habitResult = points.reconcile(userId, new ReconcileScope.Habit(habit.getId(), habit.getActiveFrom()));
        PointsResult dayResult = points.reconcile(userId, new ReconcileScope.DayCommitment(userId, date));
        streakService.rebuildCache(habit);

        return PointsResult.combine(List.of(habitResult, dayResult));
    }

    private void requireWritable(Habit habit, LocalDate date) {
        if (habit.isArchived()) {
            throw ApiException.notFound("That habit");
        }
        if (!isEditable(habit, date)) {
            throw new ApiException(ErrorCode.HABIT_LOCKED, HttpStatus.FORBIDDEN, "This day can no longer be changed.");
        }
    }

    /** Exposed for the board, which needs to tell the frontend which cells are interactive. */
    public boolean isEditable(Habit habit, LocalDate date) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(habit.getUserId()).getTimeZone());
        // Future dates are always writable (spec §4.3: "may be logged for future days").
        // Past and today follow the edit window (§4.3: "today and yesterday only").
        return dayService.isFuture(date, zone) || dayService.isWithinHabitEditWindow(date, zone);
    }
}
