package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.workout.repo.PlanExerciseRepository;
import com.dailyforge.workout.repo.WorkoutPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans, their days, and which exercises sit on which day (spec §8.2). Day 0 is the
 * Extras bucket: exercises attached to a plan but not yet placed on a real day.
 */
@Service
public class WorkoutPlanService {

    private final WorkoutPlanRepository plans;
    private final PlanExerciseRepository planExercises;
    private final ExerciseService exerciseService;

    public WorkoutPlanService(
            WorkoutPlanRepository plans, PlanExerciseRepository planExercises, ExerciseService exerciseService) {
        this.plans = plans;
        this.planExercises = planExercises;
        this.exerciseService = exerciseService;
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlan> listActive(UUID userId) {
        return plans.findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlan> listArchived(UUID userId) {
        return plans.findAllByUserIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public WorkoutPlan create(UUID userId, String name, int dayCount) {
        // The first plan a user ever creates becomes active by default — a plan the
        // tracker has nothing to preselect is a worse first run than one that guesses.
        boolean makeActive = plans.findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId).isEmpty();
        WorkoutPlan plan = WorkoutPlan.create(userId, name, dayCount, makeActive);
        return plans.save(plan);
    }

    @Transactional
    public WorkoutPlan update(UUID id, UUID userId, String name, Boolean active) {
        WorkoutPlan plan = requireOwned(id, userId);
        if (name != null) {
            plan.rename(name);
        }
        if (Boolean.TRUE.equals(active)) {
            deactivateAllExcept(userId, id);
            plan.activate();
        } else if (Boolean.FALSE.equals(active)) {
            plan.deactivate();
        }
        return plans.save(plan);
    }

    @Transactional
    public WorkoutPlan relabelDay(UUID id, UUID userId, int dayIndex, String label) {
        WorkoutPlan plan = requireOwned(id, userId);
        requireValidDayIndex(plan, dayIndex);
        plan.relabelDay(dayIndex, label);
        return plans.save(plan);
    }

    @Transactional
    public void archive(UUID id, UUID userId) {
        WorkoutPlan plan = requireOwned(id, userId);
        plan.archive(Instant.now());
        plans.save(plan);
    }

    @Transactional
    public WorkoutPlan unarchive(UUID id, UUID userId) {
        WorkoutPlan plan = requireOwned(id, userId);
        plan.unarchive();
        return plans.save(plan);
    }

    @Transactional(readOnly = true)
    public List<PlanExercise> exercisesFor(UUID planId) {
        return planExercises.findAllByPlanIdOrderByDayIndexAscSortOrderAsc(planId);
    }

    @Transactional
    public PlanExercise addExercise(
            UUID planId, UUID userId, UUID exerciseId, int dayIndex, Integer targetSets, Integer targetReps, String notes) {
        WorkoutPlan plan = requireOwned(planId, userId);
        requireValidDayIndex(plan, dayIndex);
        exerciseService.requireVisible(exerciseId, userId); // 404s if it does not exist or is not this user's

        int nextSort =
                planExercises.findAllByPlanIdAndDayIndexOrderBySortOrderAsc(planId, dayIndex).stream()
                        .mapToInt(PlanExercise::getSortOrder)
                        .max()
                        .orElse(-1)
                        + 1;

        try {
            return planExercises.save(
                    PlanExercise.create(planId, exerciseId, dayIndex, nextSort, targetSets, targetReps, notes));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(
                    com.dailyforge.common.error.ErrorCode.CONFLICT,
                    org.springframework.http.HttpStatus.CONFLICT,
                    "That exercise is already on this day.");
        }
    }

    @Transactional
    public void removeExercise(UUID planId, UUID userId, UUID planExerciseId) {
        requireOwned(planId, userId);
        PlanExercise pe =
                planExercises.findByIdAndPlanId(planExerciseId, planId).orElseThrow(() -> ApiException.notFound("That exercise"));
        planExercises.delete(pe);
    }

    /** Moves one exercise placement to another day — the "Swap" action (spec §8.2/§8.3). */
    @Transactional
    public PlanExercise moveExercise(UUID planId, UUID userId, UUID planExerciseId, int toDayIndex) {
        WorkoutPlan plan = requireOwned(planId, userId);
        requireValidDayIndex(plan, toDayIndex);
        PlanExercise pe =
                planExercises.findByIdAndPlanId(planExerciseId, planId).orElseThrow(() -> ApiException.notFound("That exercise"));

        int nextSort =
                planExercises.findAllByPlanIdAndDayIndexOrderBySortOrderAsc(planId, toDayIndex).stream()
                        .mapToInt(PlanExercise::getSortOrder)
                        .max()
                        .orElse(-1)
                        + 1;
        pe.moveTo(toDayIndex, nextSort);
        return planExercises.save(pe);
    }

    /** Reorders every exercise on one day to match the given sequence (keyboard-reachable per spec §8.2). */
    @Transactional
    public void reorderDay(UUID planId, UUID userId, int dayIndex, List<UUID> orderedPlanExerciseIds) {
        requireOwned(planId, userId);
        for (int i = 0; i < orderedPlanExerciseIds.size(); i++) {
            PlanExercise pe =
                    planExercises
                            .findByIdAndPlanId(orderedPlanExerciseIds.get(i), planId)
                            .orElseThrow(() -> ApiException.notFound("That exercise"));
            pe.updateSortOrder(i);
            planExercises.save(pe);
        }
    }

    public WorkoutPlan requireOwned(UUID id, UUID userId) {
        return plans.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That plan"));
    }

    private void requireValidDayIndex(WorkoutPlan plan, int dayIndex) {
        if (dayIndex < 0 || dayIndex > plan.getDayCount()) {
            throw ApiException.outOfRange("dayIndex", "That day does not exist on this plan.");
        }
    }

    private void deactivateAllExcept(UUID userId, UUID keepId) {
        plans.findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(p -> !p.getId().equals(keepId) && p.isActive())
                .forEach(
                        p -> {
                            p.deactivate();
                            plans.save(p);
                        });
    }
}
