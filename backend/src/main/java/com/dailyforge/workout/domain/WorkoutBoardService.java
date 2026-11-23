package com.dailyforge.workout.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.PlanExerciseRepository;
import com.dailyforge.workout.repo.WorkoutExerciseCompletionRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read model behind {@code GET /workouts/session} (spec §8.3): one call, every
 * exercise for the day with its sets and both PR figures, grouped the way the tracker
 * renders log cards. A planned session's exercise list comes from the plan day itself
 * (so an exercise with zero sets still shows up, ready to log); a freeform session's
 * list is whatever has actually been logged, since there is no plan to enumerate from.
 */
@Service
public class WorkoutBoardService {

    private final WorkoutSetService setService;
    private final PlanExerciseRepository planExercises;
    private final ExerciseRepository exercises;
    private final DayService dayService;
    private final IdentityService identity;
    private final WorkoutExerciseCompletionRepository completions;
    private final DeloadService deload;

    public WorkoutBoardService(
            WorkoutSetService setService,
            PlanExerciseRepository planExercises,
            ExerciseRepository exercises,
            DayService dayService,
            IdentityService identity,
            WorkoutExerciseCompletionRepository completions,
            DeloadService deload) {
        this.setService = setService;
        this.planExercises = planExercises;
        this.exercises = exercises;
        this.dayService = dayService;
        this.identity = identity;
        this.completions = completions;
        this.deload = deload;
    }

    /**
     * {@code completedAt} is null until the user says they are finished with this
     * exercise. It is the third state the board needs: sets logged means work started,
     * not work finished.
     */
    public record BoardExercise(
            Exercise exercise,
            List<WorkoutSet> sets,
            WorkoutSetService.PrView recentPr,
            WorkoutSetService.PrView lifetimePr,
            java.time.Instant completedAt,
            /** From the plan day; null on a freeform session or when no target was set. */
            Integer targetSets,
            Integer targetReps,
            Integer targetRepsMax,
            String notes,
            /** Present only when last time hit every target: time to go up in weight. */
            ProgressionCalculator.Suggestion progression,
            UUID planExerciseId,
            Integer restSeconds) {}

    @Transactional(readOnly = true)
    public List<BoardExercise> board(UUID userId, WorkoutSession session) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        List<WorkoutSet> loggedSets = setService.setsFor(session.getId());
        boolean deloadWeek = deload.isDeload(userId, session.getOccurredOn());
        Map<UUID, java.time.Instant> completedAt =
                completions.findAllBySessionId(session.getId()).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        WorkoutExerciseCompletion::getExerciseId, WorkoutExerciseCompletion::getCompletedAt));

        Set<UUID> exerciseIds = new LinkedHashSet<>();
        Map<UUID, PlanExercise> planned = new java.util.HashMap<>();
        if (session.getPlanId() != null && session.getDayIndex() != null) {
            planExercises
                    .findAllByPlanIdAndDayIndexOrderBySortOrderAsc(session.getPlanId(), session.getDayIndex())
                    .forEach(pe -> {
                        exerciseIds.add(pe.getExerciseId());
                        planned.put(pe.getExerciseId(), pe);
                    });
        }
        loggedSets.forEach(s -> exerciseIds.add(s.getExerciseId()));

        List<BoardExercise> board = new ArrayList<>();
        for (UUID exerciseId : exerciseIds) {
            Exercise exercise = exercises.findById(exerciseId).orElse(null);
            if (exercise == null) {
                continue;
            }
            List<WorkoutSet> sets = loggedSets.stream().filter(s -> s.getExerciseId().equals(exerciseId)).toList();
            PlanExercise pe = planned.get(exerciseId);
            Integer plannedSets = pe == null ? null : pe.getTargetSets();
            Integer targetSets = deloadWeek ? DeloadService.deloadSets(plannedSets) : plannedSets;
            Integer targetReps = pe == null ? null : pe.getTargetReps();
            Integer targetRepsMax = pe == null ? null : pe.getTargetRepsMax();
            // "Reps, then weight": the hint waits for every set to reach the TOP of the range.
            Integer repsToProgress = pe == null ? null : pe.repsToProgress();
            board.add(
                    new BoardExercise(
                            exercise,
                            sets,
                            setService.lastSessionBest(userId, exerciseId, session.getOccurredOn()),
                            setService.lifetimePr(userId, exerciseId),
                            completedAt.get(exerciseId),
                            targetSets,
                            targetReps,
                            targetRepsMax,
                            pe == null ? null : pe.getNotes(),
                            // A deload week goes lighter on purpose: no nudge to add weight.
                            deloadWeek
                                    ? null
                                    : setService
                                            .progression(userId, exercise, targetSets, repsToProgress, session.getOccurredOn())
                                            .orElse(null),
                            pe == null ? null : pe.getId(),
                            pe == null ? null : pe.getRestSeconds()));
        }
        return board;
    }
}
