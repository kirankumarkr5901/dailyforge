package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.workout.repo.PlanExerciseRepository;
import com.dailyforge.workout.repo.WorkoutExerciseCompletionRepository;
import com.dailyforge.workout.repo.WorkoutSessionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marking an exercise finished for a session, and taking it back.
 *
 * <p>Carries no points of its own, deliberately. Points are for work done, and the work
 * was already paid for when the sets were logged — paying again for saying "that's me
 * done" would be paying twice for one effort, and would make the button worth pressing
 * whether or not it was true.
 *
 * <p>It is, though, where the session finishes. The session is complete when every
 * exercise planned for the day is marked complete, not when each has a set: logging the
 * last exercise's first set used to "complete" the session, and every set after that
 * celebrated again. The celebration (zero points, spec §5.4) now fires once, on the mark
 * that finishes the day.
 */
@Service
public class WorkoutCompletionService {

    private final WorkoutExerciseCompletionRepository completions;
    private final WorkoutSessionRepository sessions;
    private final PlanExerciseRepository planExercises;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;

    public WorkoutCompletionService(
            WorkoutExerciseCompletionRepository completions,
            WorkoutSessionRepository sessions,
            PlanExerciseRepository planExercises,
            PointsService points,
            PointsRuleConfigService ruleConfigs) {
        this.completions = completions;
        this.sessions = sessions;
        this.planExercises = planExercises;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
    }

    /**
     * Marks one exercise finished, and finishes the session if this was the last one.
     * Idempotent: pressing it twice is the same as pressing it once, which matters because
     * the button and the network can both stutter.
     */
    @Transactional
    public PointsResult markComplete(UUID sessionId, UUID exerciseId, UUID userId) {
        WorkoutSession session = requireOwnedSession(sessionId, userId);
        if (completions.findBySessionIdAndExerciseId(sessionId, exerciseId).isEmpty()) {
            try {
                completions.saveAndFlush(WorkoutExerciseCompletion.create(sessionId, exerciseId, Instant.now()));
            } catch (DataIntegrityViolationException raced) {
                // Two taps arrived together; the loser here is the winner of an idempotent
                // retry, exactly as in the points engine's own award path.
            }
        }
        return checkSessionComplete(session);
    }

    /** Takes the mark back — you were not as done as you thought. */
    @Transactional
    public void clearComplete(UUID sessionId, UUID exerciseId, UUID userId) {
        requireOwnedSession(sessionId, userId);
        completions.deleteBySessionIdAndExerciseId(sessionId, exerciseId);
    }

    /**
     * Fires the session-complete celebration the first time every exercise planned for
     * this day is marked complete. Only a session opened against a real plan day can
     * complete: a freeform session has no fixed list to be complete against. Once the
     * session is complete it stays complete, so reopening and re-finishing an exercise
     * does not celebrate a second time.
     */
    private PointsResult checkSessionComplete(WorkoutSession session) {
        if (session.isCompleted() || session.getPlanId() == null || session.getDayIndex() == null) {
            return zeroResult(session.getUserId());
        }
        var config = ruleConfigs.getSystemDefault("WORKOUT_SESSION_COMPLETE");
        if (!config.enabled()) {
            return zeroResult(session.getUserId());
        }
        Set<UUID> required =
                planExercises.findAllByPlanIdAndDayIndexOrderBySortOrderAsc(session.getPlanId(), session.getDayIndex()).stream()
                        .map(PlanExercise::getExerciseId)
                        .collect(Collectors.toSet());
        Set<UUID> done =
                completions.findAllBySessionId(session.getId()).stream()
                        .map(WorkoutExerciseCompletion::getExerciseId)
                        .collect(Collectors.toSet());
        if (required.isEmpty() || !done.containsAll(required)) {
            return zeroResult(session.getUserId());
        }

        session.markCompleted(Instant.now());
        sessions.save(session);
        return points.award(
                AwardCommand.withoutSource(
                        session.getUserId(),
                        session.getOccurredOn(),
                        PointsCategory.WORKOUT,
                        "WORKOUT_SESSION_COMPLETE",
                        config.getInt("points"),
                        "Session complete",
                        "workout-session-complete:" + session.getId()));
    }

    private PointsResult zeroResult(UUID userId) {
        return new PointsResult(List.of(), 0, points.snapshot(userId, ZoneId.of("UTC")).total(), List.of());
    }

    private WorkoutSession requireOwnedSession(UUID sessionId, UUID userId) {
        return sessions
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "That session does not exist."));
    }
}
