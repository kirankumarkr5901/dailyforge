package com.dailyforge.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One exercise's placement in a plan, on one day. {@code dayIndex} 0 is the Extras
 * bucket (spec §8.2). An elite exercise (spec §8.2) placed on several days is several
 * rows sharing the same {@code exerciseId} — the log it produces is still keyed by
 * {@code (user, exercise, date)}, not by which of those rows it came from.
 */
@Entity
@Table(name = "plan_exercise")
public class PlanExercise {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "target_sets")
    private Integer targetSets;

    @Column(name = "target_reps")
    private Integer targetReps;

    @Column(name = "notes", length = 200)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanExercise() {
        // for JPA
    }

    public static PlanExercise create(
            UUID planId, UUID exerciseId, int dayIndex, int sortOrder, Integer targetSets, Integer targetReps, String notes) {
        PlanExercise pe = new PlanExercise();
        pe.id = UUID.randomUUID();
        pe.planId = planId;
        pe.exerciseId = exerciseId;
        pe.dayIndex = dayIndex;
        pe.sortOrder = sortOrder;
        pe.targetSets = targetSets;
        pe.targetReps = targetReps;
        pe.notes = notes;
        return pe;
    }

    public void moveTo(int dayIndex, int sortOrder) {
        this.dayIndex = dayIndex;
        this.sortOrder = sortOrder;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updateTargets(Integer targetSets, Integer targetReps, String notes) {
        this.targetSets = targetSets;
        this.targetReps = targetReps;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Integer getTargetSets() {
        return targetSets;
    }

    public Integer getTargetReps() {
        return targetReps;
    }

    public String getNotes() {
        return notes;
    }
}
