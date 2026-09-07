package com.dailyforge.workout.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.workout.api.WorkoutDtos.CreateExerciseRequest;
import com.dailyforge.workout.api.WorkoutDtos.ExerciseResponse;
import com.dailyforge.workout.api.WorkoutDtos.HistoryEntryResponse;
import com.dailyforge.workout.api.WorkoutDtos.UpdateExerciseRequest;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WorkoutSetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The shared exercise catalog (spec §6, §8.2's "search field that suggests from the shared catalog"). */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exercises;
    private final WorkoutSetService sets;
    private final CurrentUser currentUser;

    public ExerciseController(ExerciseService exercises, WorkoutSetService sets, CurrentUser currentUser) {
        this.exercises = exercises;
        this.sets = sets;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ExerciseResponse> search(@RequestParam(required = false) String q) {
        UUID userId = currentUser.require();
        return exercises.search(userId, q).stream().map(e -> ExerciseResponse.of(e, userId)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseResponse create(@Valid @RequestBody CreateExerciseRequest request) {
        UUID userId = currentUser.require();
        Exercise exercise =
                exercises.create(
                        userId,
                        request.name().trim(),
                        request.kind(),
                        request.equipment(),
                        request.muscleGroups() != null ? request.muscleGroups() : List.of(),
                        Boolean.TRUE.equals(request.isElite()));
        return ExerciseResponse.of(exercise, userId);
    }

    @PatchMapping("/{id}")
    public ExerciseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateExerciseRequest request) {
        UUID userId = currentUser.require();
        Exercise exercise =
                exercises.update(
                        id, userId, request.name(), request.kind(), request.equipment(), request.muscleGroups(), request.isElite());
        return ExerciseResponse.of(exercise, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        exercises.deleteOrArchive(id, currentUser.require());
    }

    /** Every set ever logged for this exercise, newest first (spec §11's history view). */
    @GetMapping("/{id}/history")
    public List<HistoryEntryResponse> history(@PathVariable UUID id) {
        UUID userId = currentUser.require();
        exercises.requireVisible(id, userId); // 404s if this exercise is not visible to the caller
        return sets.history(userId, id).stream().map(HistoryEntryResponse::of).toList();
    }
}
