package com.dailyforge.goal.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.goal.repo.GoalRepository;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitStreakService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goals (spec §5.4, §8.6): progress is computed on read from each kind's own module
 * (habit, workout, run, body), never pushed to reactively on every write — the same
 * on-demand pattern M4 and M5 used for a PR view, applied here across four different
 * source modules instead of one. Completing a goal is a direct award, reversed on
 * reopen, exactly like a workout session's completion celebration — not routed through
 * the reconciliation engine, because "the current goal state" has no history to diff
 * against the way a PR or a streak does.
 */
@Service
public class GoalService {

    private final GoalRepository goals;
    private final PointsService points;
    private final HabitService habitService;
    private final HabitStreakService habitStreakService;
    private final WorkoutSetService workoutSetService;
    private final RunService runService;
    private final BodyMetricService bodyMetricService;
    private final DayService dayService;
    private final IdentityService identity;

    public GoalService(
            GoalRepository goals,
            PointsService points,
            HabitService habitService,
            HabitStreakService habitStreakService,
            WorkoutSetService workoutSetService,
            RunService runService,
            BodyMetricService bodyMetricService,
            DayService dayService,
            IdentityService identity) {
        this.goals = goals;
        this.points = points;
        this.habitService = habitService;
        this.habitStreakService = habitStreakService;
        this.workoutSetService = workoutSetService;
        this.runService = runService;
        this.bodyMetricService = bodyMetricService;
        this.dayService = dayService;
        this.identity = identity;
    }

    @Transactional
    public Goal create(
            UUID userId,
            String title,
            String description,
            GoalKind kind,
            GoalPeriodType periodType,
            LocalDate startDate,
            LocalDate targetDate,
            Integer rewardPoints,
            UUID habitId,
            UUID exerciseId,
            BigDecimal targetValue) {
        if (kind == GoalKind.HABIT_ADHERENCE) {
            if (habitId == null || targetValue == null) {
                throw ApiException.outOfRange("habitId", "A habit-adherence goal needs a habit and a day count.");
            }
            habitService.requireOwned(habitId, userId); // 404s if it is not this user's
        }
        if (kind == GoalKind.EXERCISE_TARGET) {
            if (exerciseId == null || targetValue == null) {
                throw ApiException.outOfRange("exerciseId", "An exercise-target goal needs an exercise and a weight.");
            }
        }
        if ((kind == GoalKind.RUN_DISTANCE || kind == GoalKind.BODY_METRIC) && targetValue == null) {
            throw ApiException.outOfRange("targetValue", "That kind of goal needs a target value.");
        }

        LocalDate endDate = resolveEndDate(periodType, startDate, targetDate);
        int reward = rewardPoints != null ? rewardPoints : 100;

        Goal goal = Goal.create(userId, title, description, kind, periodType, startDate, endDate, reward, habitId, exerciseId, targetValue);
        return goals.save(refreshProgress(goal));
    }

    private LocalDate resolveEndDate(GoalPeriodType periodType, LocalDate startDate, LocalDate targetDate) {
        return switch (periodType) {
            case WEEK -> startDate.plusDays(6);
            case MONTH -> startDate.with(TemporalAdjusters.lastDayOfMonth());
            case YEAR -> startDate.with(TemporalAdjusters.lastDayOfYear());
            case TARGET_DATE -> {
                if (targetDate == null) {
                    throw ApiException.outOfRange("targetDate", "A target-date goal needs a target date.");
                }
                yield targetDate;
            }
        };
    }

    @Transactional
    public List<Goal> list(UUID userId, GoalStatus status) {
        List<Goal> found = status != null ? goals.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, status) : goals.findAllByUserIdOrderByCreatedAtDesc(userId);
        return found.stream().map(this::refreshProgress).map(goals::save).toList();
    }

    /**
     * Recomputes {@code currentValue} from the owning module's own data, and
     * auto-completes a goal that has just reached its target — except HABIT_ADHERENCE,
     * which instead sits at "reached, not yet claimed" until {@link #complete} is
     * called: owner feedback wanted an explicit claim moment for a habit goal rather
     * than it completing silently on whatever read happened to notice.
     */
    private Goal refreshProgress(Goal goal) {
        if (goal.getStatus() != GoalStatus.ACTIVE || goal.getKind() == GoalKind.CUSTOM) {
            return goal;
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(goal.getUserId()).getTimeZone());
        LocalDate today = dayService.today(zone);
        LocalDate progressTo = goal.getEndDate() != null && goal.getEndDate().isBefore(today) ? goal.getEndDate() : today;

        BigDecimal current =
                switch (goal.getKind()) {
                    // A streak, not a scattered count: reaching the target means that
                    // many *consecutive* scheduled days done, the same "current streak"
                    // the habit tracker itself shows, not just N done days anywhere in
                    // the goal's period (owner feedback — "the days to complete should
                    // be a streak").
                    case HABIT_ADHERENCE -> {
                        var habit = habitService.requireOwned(goal.getHabitId(), goal.getUserId());
                        yield BigDecimal.valueOf(habitStreakService.compute(habit, goal.getStartDate()).currentStreak());
                    }
                    case EXERCISE_TARGET -> {
                        var pr = workoutSetService.lifetimePr(goal.getUserId(), goal.getExerciseId());
                        yield pr != null ? pr.totalWeightKg() : BigDecimal.ZERO;
                    }
                    case RUN_DISTANCE -> BigDecimal.valueOf(runService.totalDistanceMeters(goal.getUserId(), goal.getStartDate(), progressTo) / 1000.0);
                    case BODY_METRIC -> {
                        var weight = bodyMetricService.latestWeightKg(goal.getUserId(), progressTo);
                        yield weight != null ? weight : BigDecimal.ZERO;
                    }
                    case CUSTOM -> goal.getCurrentValue();
                };
        goal.updateProgress(current);

        if (goal.isComplete() && goal.getKind() != GoalKind.HABIT_ADHERENCE) {
            awardCompletion(goal);
        }
        return goal;
    }

    /**
     * Also the claim action for a habit-adherence goal once its streak has reached
     * target (see {@link #refreshProgress}) — CUSTOM's own manual "Mark complete" and a
     * habit goal's "Claim" button both land here. Anything else must actually be
     * complete first: unlike CUSTOM, whose only measure of done is the user saying so,
     * a measurable goal claimed early would hand out points nothing was earned.
     */
    @Transactional
    public Goal complete(UUID id, UUID userId) {
        Goal goal = requireOwned(id, userId);
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That goal is not active.");
        }
        if (goal.getKind() != GoalKind.CUSTOM && !goal.isComplete()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That goal has not reached its target yet.");
        }
        awardCompletion(goal);
        return goals.save(goal);
    }

    private static final String SOURCE_TYPE = "GOAL";

    private void awardCompletion(Goal goal) {
        goal.complete(Instant.now());
        points.award(
                new AwardCommand(
                        goal.getUserId(),
                        dayService.today(dayService.zoneOf(identity.requireSettings(goal.getUserId()).getTimeZone())),
                        PointsCategory.GOAL,
                        "GOAL_COMPLETE",
                        goal.getRewardPoints(),
                        SOURCE_TYPE,
                        goal.getId(),
                        "Goal complete — " + goal.getTitle(),
                        "goal-complete:" + goal.getId()));
    }

    /** Reversed exactly like any other source-tracked award (spec §5.4: "Reversed if the goal is reopened"). */
    @Transactional
    public Goal reopen(UUID id, UUID userId) {
        Goal goal = requireOwned(id, userId);
        if (goal.getStatus() != GoalStatus.COMPLETED) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "That goal has not been completed.");
        }
        points.reverseBySource(SOURCE_TYPE, goal.getId(), "Goal reopened");
        goal.reopen();
        return goals.save(goal);
    }

    @Transactional
    public Goal extend(UUID id, UUID userId, LocalDate newEndDate) {
        Goal goal = requireOwned(id, userId);
        if (goal.getStatus() != GoalStatus.FAILED) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Only a failed goal can be extended.");
        }
        goal.extend(newEndDate);
        return goals.save(goal);
    }

    @Transactional
    public Goal archive(UUID id, UUID userId) {
        Goal goal = requireOwned(id, userId);
        goal.archive();
        return goals.save(goal);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Goal goal = requireOwned(id, userId);
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            points.reverseBySource(SOURCE_TYPE, goal.getId(), "Goal deleted");
        }
        goals.delete(goal);
    }

    public Goal requireOwned(UUID id, UUID userId) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That goal"));
    }
}
