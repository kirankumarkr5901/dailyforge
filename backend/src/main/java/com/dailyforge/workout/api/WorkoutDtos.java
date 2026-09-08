package com.dailyforge.workout.api;

import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.PlanExercise;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutPlan;
import com.dailyforge.workout.domain.WorkoutSession;
import com.dailyforge.workout.domain.WorkoutSet;
import com.dailyforge.workout.domain.WorkoutSetService.PrView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkoutDtos {

    private WorkoutDtos() {}

    // ---------------------------------------------------------------- exercises

    /**
     * {@code isElite} is boxed, not a primitive {@code boolean}: the frontend's create
     * form did not send it at all until the elite toggle was added, and Jackson cannot
     * bind a missing JSON field to a primitive on a record's canonical constructor — it
     * threw a body-parse error for every request that omitted it, not a validation
     * error naming the field. A boxed {@code Boolean}, defaulted in the controller,
     * degrades to "not elite" instead of rejecting the whole request.
     */
    public record CreateExerciseRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull ExerciseKind kind,
            @NotNull Equipment equipment,
            List<String> muscleGroups,
            Boolean isElite) {}

    public record UpdateExerciseRequest(
            @Size(max = 120) String name, ExerciseKind kind, Equipment equipment, List<String> muscleGroups, Boolean isElite) {}

    public record ExerciseResponse(
            UUID id,
            String name,
            ExerciseKind kind,
            Equipment equipment,
            List<String> muscleGroups,
            boolean isElite,
            boolean ownedByMe,
            boolean archived,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static ExerciseResponse of(Exercise exercise, UUID viewerId) {
            return new ExerciseResponse(
                    exercise.getId(),
                    exercise.getName(),
                    exercise.getKind(),
                    exercise.getEquipment(),
                    exercise.getMuscleGroups(),
                    exercise.isElite(),
                    exercise.isOwnedBy(viewerId),
                    exercise.isArchived(),
                    exercise.getVersion());
        }
    }

    // ---------------------------------------------------------------- plans

    public record CreatePlanRequest(@NotBlank @Size(max = 80) String name, @NotNull Integer dayCount) {}

    public record UpdatePlanRequest(@Size(max = 80) String name, Boolean isActive) {}

    public record RelabelDayRequest(@NotBlank @Size(max = 40) String label) {}

    public record AddPlanExerciseRequest(
            @NotNull UUID exerciseId, @NotNull Integer dayIndex, Integer targetSets, Integer targetReps, String notes) {}

    public record MoveExerciseRequest(@NotNull Integer toDayIndex) {}

    public record UpdatePlanExerciseRequest(Integer targetSets, Integer targetReps, @Size(max = 200) String notes) {}

    public record ReorderDayRequest(@NotNull List<UUID> orderedPlanExerciseIds) {}

    public record PlanExerciseResponse(
            UUID id,
            UUID exerciseId,
            String exerciseName,
            List<String> muscleGroups,
            boolean isElite,
            int dayIndex,
            int sortOrder,
            Integer targetSets,
            Integer targetReps,
            String notes) {

        public static PlanExerciseResponse of(PlanExercise pe, Exercise exercise) {
            return new PlanExerciseResponse(
                    pe.getId(),
                    pe.getExerciseId(),
                    exercise.getName(),
                    exercise.getMuscleGroups(),
                    exercise.isElite(),
                    pe.getDayIndex(),
                    pe.getSortOrder(),
                    pe.getTargetSets(),
                    pe.getTargetReps(),
                    pe.getNotes());
        }
    }

    public record PlanResponse(
            UUID id,
            String name,
            int dayCount,
            List<String> dayLabels,
            boolean isActive,
            List<PlanExerciseResponse> exercises,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static PlanResponse of(WorkoutPlan plan, List<PlanExerciseResponse> exercises) {
            List<String> labels =
                    java.util.stream.IntStream.rangeClosed(1, plan.getDayCount())
                            .mapToObj(plan::labelFor)
                            .toList();
            return new PlanResponse(
                    plan.getId(), plan.getName(), plan.getDayCount(), labels, plan.isActive(), exercises, plan.getVersion());
        }
    }

    // ---------------------------------------------------------------- sessions & sets

    public record LogSetRequest(
            @NotNull LocalDate date,
            @NotNull UUID exerciseId,
            UUID planId,
            Integer dayIndex,
            BigDecimal enteredWeight,
            @NotNull WeightMode weightMode,
            BigDecimal addedWeight,
            @NotNull Integer reps) {}

    public record UpdateSetRequest(
            BigDecimal enteredWeight, @NotNull WeightMode weightMode, BigDecimal addedWeight, @NotNull Integer reps) {}

    public record PrResponse(BigDecimal totalWeightKg, int reps, LocalDate achievedOn) {
        public static PrResponse of(PrView view) {
            return view == null ? null : new PrResponse(view.totalWeightKg(), view.reps(), view.achievedOn());
        }
    }

    public record SetResponse(
            UUID id,
            UUID exerciseId,
            int setNumber,
            BigDecimal enteredWeight,
            WeightMode weightMode,
            BigDecimal addedWeight,
            int reps,
            BigDecimal totalWeightKg,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static SetResponse of(WorkoutSet set) {
            return new SetResponse(
                    set.getId(),
                    set.getExerciseId(),
                    set.getSetNumber(),
                    set.getEnteredWeight(),
                    set.getWeightMode(),
                    set.getAddedWeight(),
                    set.getReps(),
                    set.getTotalWeightKg(),
                    set.getVersion());
        }
    }

    public record HistoryEntryResponse(LocalDate date, SetResponse set) {
        public static HistoryEntryResponse of(com.dailyforge.workout.domain.WorkoutSetService.HistoryEntry entry) {
            return new HistoryEntryResponse(entry.date(), SetResponse.of(entry.set()));
        }
    }

    public record ExerciseBoardEntry(
            UUID exerciseId,
            String name,
            ExerciseKind kind,
            Equipment equipment,
            List<String> muscleGroups,
            boolean isElite,
            PrResponse recentPr,
            PrResponse lifetimePr,
            List<SetResponse> sets) {}

    public record SessionResponse(
            UUID id, LocalDate date, UUID planId, Integer dayIndex, boolean completed, List<ExerciseBoardEntry> exercises) {}

    /** A set write returns the affected set plus the points envelope (spec §7). */
    public record SetWriteResponse(SetResponse set, PointsEnvelope points) {
        public static SetWriteResponse of(WorkoutSet set, PointsResult result) {
            return new SetWriteResponse(SetResponse.of(set), PointsEnvelope.of(result));
        }
    }

    public record DeleteSetResponse(PointsEnvelope points) {
        public static DeleteSetResponse of(PointsResult result) {
            return new DeleteSetResponse(PointsEnvelope.of(result));
        }
    }
}
