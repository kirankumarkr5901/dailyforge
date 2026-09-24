package com.dailyforge.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One logged set. {@code totalWeightKg} is derived and stored, never recomputed ad hoc
 * at read time, so the PR ordering (spec §5.5) it drives is stable even if the formula
 * ever changes for new sets. Soft-deleted rather than removed, so a set that once held a
 * PR stays in the history the PR reconciler diffs against (spec §5.3's own mechanism).
 */
@Entity
@Table(name = "workout_set")
public class WorkoutSet {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "entered_weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal enteredWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_mode", nullable = false, length = 10)
    private WeightMode weightMode;

    @Column(name = "added_weight", precision = 6, scale = 2)
    private BigDecimal addedWeight;

    @Column(name = "reps", nullable = false)
    private int reps;

    @Column(name = "total_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalWeightKg;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Optimistic-concurrency guard for multi-device editing (see StaleWrite). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkoutSet() {
        // for JPA
    }

    public static WorkoutSet log(
            UUID sessionId,
            UUID exerciseId,
            Equipment equipment,
            int setNumber,
            BigDecimal enteredWeight,
            WeightMode weightMode,
            BigDecimal addedWeight,
            int reps) {
        WorkoutSet set = new WorkoutSet();
        set.id = UUID.randomUUID();
        set.sessionId = sessionId;
        set.exerciseId = exerciseId;
        set.setNumber = setNumber;
        set.reps = reps;
        set.apply(equipment, enteredWeight, weightMode, addedWeight);
        return set;
    }

    /** Re-derives {@code totalWeightKg} from the fields spec §5.5 defines it by. */
    public void apply(Equipment equipment, BigDecimal enteredWeight, WeightMode weightMode, BigDecimal addedWeight) {
        this.enteredWeight = enteredWeight == null ? BigDecimal.ZERO : enteredWeight;
        this.weightMode = weightMode;
        this.addedWeight = addedWeight;
        this.totalWeightKg = computeTotalWeightKg(equipment, this.enteredWeight, weightMode, addedWeight);
    }

    public void updateReps(int reps) {
        this.reps = reps;
    }

    /**
     * Bodyweight exercises rank by added weight only, zero if none (spec §5.5, §13.12)
     * — the user's own body weight is not tracked here, so it cannot enter the formula.
     */
    public static BigDecimal computeTotalWeightKg(
            Equipment equipment, BigDecimal enteredWeight, WeightMode weightMode, BigDecimal addedWeight) {
        if (equipment == Equipment.BODYWEIGHT) {
            return addedWeight == null ? BigDecimal.ZERO : addedWeight;
        }
        BigDecimal weight = enteredWeight == null ? BigDecimal.ZERO : enteredWeight;
        return weightMode == WeightMode.SINGLE ? weight.multiply(BigDecimal.valueOf(2)) : weight;
    }

    public void softDelete(Instant when) {
        this.deletedAt = when;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.loggedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public BigDecimal getEnteredWeight() {
        return enteredWeight;
    }

    public WeightMode getWeightMode() {
        return weightMode;
    }

    public BigDecimal getAddedWeight() {
        return addedWeight;
    }

    public int getReps() {
        return reps;
    }

    public BigDecimal getTotalWeightKg() {
        return totalWeightKg;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
    public long getVersion() {
        return version;
    }

}
