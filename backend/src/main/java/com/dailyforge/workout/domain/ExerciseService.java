package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared exercise catalog (spec §6): a system row (owner null) is visible to
 * everyone, a user's own row only to them. Both live in the same table and the same
 * search, so adding an exercise to a plan never has to care which kind it picked.
 */
@Service
public class ExerciseService {

    private final ExerciseRepository exercises;
    private final WorkoutSetRepository sets;

    public ExerciseService(ExerciseRepository exercises, WorkoutSetRepository sets) {
        this.exercises = exercises;
        this.sets = sets;
    }

    @Transactional(readOnly = true)
    public List<Exercise> search(UUID userId, String query) {
        String normalised = query == null || query.isBlank() ? null : Exercise.normalise(query);
        return exercises.search(userId, normalised);
    }

    @Transactional
    public Exercise create(
            UUID userId, String name, ExerciseKind kind, Equipment equipment, List<String> muscleGroups, boolean elite) {
        Exercise exercise = Exercise.create(userId, name, kind, equipment, muscleGroups, elite);
        return exercises.save(exercise);
    }

    @Transactional
    public Exercise update(
            UUID id,
            UUID userId,
            String name,
            ExerciseKind kind,
            Equipment equipment,
            List<String> muscleGroups,
            boolean elite) {
        Exercise exercise = requireOwned(id, userId);
        exercise.update(
                name != null ? name : exercise.getName(),
                kind != null ? kind : exercise.getKind(),
                equipment != null ? equipment : exercise.getEquipment(),
                muscleGroups != null ? muscleGroups : exercise.getMuscleGroups(),
                elite);
        return exercises.save(exercise);
    }

    /** Hard-deletes an exercise with no logs; archives (keeps history and PRs) one that has any. */
    @Transactional
    public void deleteOrArchive(UUID id, UUID userId) {
        Exercise exercise = requireOwned(id, userId);
        boolean hasLogs = !sets.findAllForUserAndExercise(userId, id).isEmpty();
        if (hasLogs) {
            exercise.archive(Instant.now());
            exercises.save(exercise);
        } else {
            exercises.delete(exercise);
        }
    }

    public Exercise requireVisible(UUID id, UUID userId) {
        Exercise exercise =
                exercises.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> ApiException.notFound("That exercise"));
        if (!exercise.isVisibleTo(userId)) {
            throw ApiException.forbidden();
        }
        return exercise;
    }

    private Exercise requireOwned(UUID id, UUID userId) {
        Exercise exercise = exercises.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> ApiException.notFound("That exercise"));
        if (!exercise.isOwnedBy(userId)) {
            // A system catalog exercise (owner null) is visible to everyone but owned
            // by no one — nobody may edit or delete it, only add their own instead.
            throw ApiException.forbidden();
        }
        return exercise;
    }
}
