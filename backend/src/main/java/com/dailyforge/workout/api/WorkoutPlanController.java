package com.dailyforge.workout.api;

import com.dailyforge.common.security.StructuralChange;
import com.dailyforge.common.error.StaleWrite;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.workout.api.WorkoutDtos.AddPlanExerciseRequest;
import com.dailyforge.workout.api.WorkoutDtos.CreatePlanRequest;
import com.dailyforge.workout.api.WorkoutDtos.MoveExerciseRequest;
import com.dailyforge.workout.api.WorkoutDtos.PlanExerciseResponse;
import com.dailyforge.workout.api.WorkoutDtos.PlanResponse;
import com.dailyforge.workout.api.WorkoutDtos.RelabelDayRequest;
import com.dailyforge.workout.api.WorkoutDtos.ReorderDayRequest;
import com.dailyforge.workout.api.WorkoutDtos.UpdatePlanRequest;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.PlanExercise;
import com.dailyforge.workout.domain.WorkoutPlan;
import com.dailyforge.workout.domain.WorkoutPlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workout-plans")
public class WorkoutPlanController {

    private final WorkoutPlanService plans;
    private final ExerciseService exercises;
    private final CurrentUser currentUser;
    private final com.dailyforge.common.time.DayService dayService;
    private final com.dailyforge.identity.domain.IdentityService identity;

    public WorkoutPlanController(
            WorkoutPlanService plans,
            ExerciseService exercises,
            CurrentUser currentUser,
            com.dailyforge.common.time.DayService dayService,
            com.dailyforge.identity.domain.IdentityService identity) {
        this.dayService = dayService;
        this.identity = identity;
        this.plans = plans;
        this.exercises = exercises;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<PlanResponse> list() {
        UUID userId = currentUser.require();
        return plans.listActive(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/archived")
    public List<PlanResponse> listArchived() {
        UUID userId = currentUser.require();
        return plans.listArchived(userId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/unarchive")
    @StructuralChange
    public PlanResponse unarchive(@PathVariable UUID id) {
        UUID userId = currentUser.require();
        return toResponse(plans.unarchive(id, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        UUID userId = currentUser.require();
        if (request.dayCount() < 1 || request.dayCount() > 14) {
            throw ApiException.outOfRange("dayCount", "A plan needs between 1 and 14 days.");
        }
        WorkoutPlan plan = plans.create(userId, request.name().trim(), request.dayCount());
        return toResponse(plan);
    }

    @PatchMapping("/{id}")
    @StructuralChange
    public PlanResponse update(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) Long ifMatch,
            @Valid @RequestBody UpdatePlanRequest request) {
        UUID userId = currentUser.require();
        StaleWrite.check(ifMatch, plans.requireOwned(id, userId).getVersion(), "That plan");
        WorkoutPlan plan = plans.update(id, userId, request.name(), request.isActive());
        return toResponse(plan);
    }

    @PatchMapping("/{id}/days/{dayIndex}")
    @StructuralChange
    public PlanResponse relabelDay(
            @PathVariable UUID id, @PathVariable int dayIndex, @Valid @RequestBody RelabelDayRequest request) {
        UUID userId = currentUser.require();
        WorkoutPlan plan = plans.relabelDay(id, userId, dayIndex, request.label().trim());
        return toResponse(plan);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StructuralChange
    public void archive(@PathVariable UUID id) {
        plans.archive(id, currentUser.require());
    }

    @PostMapping("/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public PlanExerciseResponse addExercise(@PathVariable UUID id, @Valid @RequestBody AddPlanExerciseRequest request) {
        UUID userId = currentUser.require();
        PlanExercise pe =
                plans.addExercise(
                        id, userId, request.exerciseId(), request.dayIndex(), request.targetSets(), request.targetReps(), request.notes());
        return PlanExerciseResponse.of(pe, exercises.requireVisible(pe.getExerciseId(), userId));
    }

    @DeleteMapping("/{id}/exercises/{planExerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StructuralChange
    public void removeExercise(@PathVariable UUID id, @PathVariable UUID planExerciseId) {
        plans.removeExercise(id, currentUser.require(), planExerciseId);
    }

    /** The plan day to preselect for {@code date}: today's day if trained, else the next after the last trained. */
    @GetMapping("/{id}/suggested-day")
    public java.util.Map<String, Object> suggestedDay(@PathVariable UUID id, @RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        // No date: today, in the user's own time zone (only the server knows it for sure).
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return java.util.Map.of("dayIndex", plans.suggestedDay(id, userId, resolved), "date", resolved);
    }

    @PatchMapping("/{id}/exercises/{planExerciseId}")
    @StructuralChange
    public PlanExerciseResponse updateExercise(
            @PathVariable UUID id,
            @PathVariable UUID planExerciseId,
            @Valid @RequestBody WorkoutDtos.UpdatePlanExerciseRequest request) {
        UUID userId = currentUser.require();
        PlanExercise pe =
                plans.updateExercise(
                        id,
                        userId,
                        planExerciseId,
                        request.targetSets(),
                        request.targetReps(),
                        request.targetRepsMax(),
                        request.notes(),
                        request.restSeconds());
        return PlanExerciseResponse.of(pe, exercises.requireVisible(pe.getExerciseId(), userId));
    }

    @PostMapping("/{id}/exercises/{planExerciseId}/move")
    @StructuralChange
    public PlanExerciseResponse moveExercise(
            @PathVariable UUID id, @PathVariable UUID planExerciseId, @Valid @RequestBody MoveExerciseRequest request) {
        UUID userId = currentUser.require();
        PlanExercise pe = plans.moveExercise(id, userId, planExerciseId, request.toDayIndex());
        return PlanExerciseResponse.of(pe, exercises.requireVisible(pe.getExerciseId(), userId));
    }

    @PatchMapping("/{id}/days/{dayIndex}/exercises/order")
    @StructuralChange
    public List<PlanExerciseResponse> reorderDay(
            @PathVariable UUID id, @PathVariable int dayIndex, @Valid @RequestBody ReorderDayRequest request) {
        UUID userId = currentUser.require();
        plans.reorderDay(id, userId, dayIndex, request.orderedPlanExerciseIds());
        return exercisesFor(id, userId).stream().filter(r -> r.dayIndex() == dayIndex).toList();
    }

    private PlanResponse toResponse(WorkoutPlan plan) {
        return WorkoutDtos.PlanResponse.of(plan, exercisesFor(plan.getId(), plan.getUserId()));
    }

    private List<PlanExerciseResponse> exercisesFor(UUID planId, UUID userId) {
        return plans.exercisesFor(planId).stream()
                .map(
                        pe -> {
                            Exercise exercise = exercises.requireVisible(pe.getExerciseId(), userId);
                            return PlanExerciseResponse.of(pe, exercise);
                        })
                .toList();
    }
}
