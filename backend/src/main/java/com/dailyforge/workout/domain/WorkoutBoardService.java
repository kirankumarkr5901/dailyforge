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

    public WorkoutBoardService(
            WorkoutSetService setService,
            PlanExerciseRepository planExercises,
            ExerciseRepository exercises,
            DayService dayService,
            IdentityService identity,
            WorkoutExerciseCompletionRepository completions) {
        this.setService = setService;
        this.planExercises = planExercises;
        this.exercises = exercises;
        this.dayService = dayService;
        this.identity = identity;
        this.completions = completions;
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
            java.time.Instant completedAt) {}

    @Transactional(readOnly = true)
    public List<BoardExercise> board(UUID userId, WorkoutSession session) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        List<WorkoutSet> loggedSets = setService.setsFor(session.getId());
        Map<UUID, java.time.Instant> completedAt =
                completions.findAllBySessionId(session.getId()).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        WorkoutExerciseCompletion::getExerciseId, WorkoutExerciseCompletion::getCompletedAt));

        Set<UUID> exerciseIds = new LinkedHashSet<>();
        if (session.getPlanId() != null && session.getDayIndex() != null) {
            planExercises
                    .findAllByPlanIdAndDayIndexOrderBySortOrderAsc(session.getPlanId(), session.getDayIndex())
                    .forEach(pe -> exerciseIds.add(pe.getExerciseId()));
        }
        loggedSets.forEach(s -> exerciseIds.add(s.getExerciseId()));

        List<BoardExercise> board = new ArrayList<>();
        for (UUID exerciseId : exerciseIds) {
            Exercise exercise = exercises.findById(exerciseId).orElse(null);
            if (exercise == null) {
                continue;
            }
            List<WorkoutSet> sets = loggedSets.stream().filter(s -> s.getExerciseId().equals(exerciseId)).toList();
            board.add(
                    new BoardExercise(
                            exercise,
                            sets,
                            setService.recentPr(userId, exerciseId, today),
                            setService.lifetimePr(userId, exerciseId),
                            completedAt.get(exerciseId)));
        }
        return board;
    }
}
