package com.dailyforge.goal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One goal (spec §8.6). See the migration's own note on why the target lives on this row directly. */
@Entity
@Table(name = "goal")
public class Goal {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private GoalKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private GoalPeriodType periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "habit_id")
    private UUID habitId;

    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "current_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Goal() {
        // for JPA
    }

    public static Goal create(
            UUID userId,
            String title,
            String description,
            GoalKind kind,
            GoalPeriodType periodType,
            LocalDate startDate,
            LocalDate endDate,
            int rewardPoints,
            UUID habitId,
            UUID exerciseId,
            BigDecimal targetValue) {
        Goal goal = new Goal();
        goal.id = UUID.randomUUID();
        goal.userId = userId;
        goal.title = title;
        goal.description = description;
        goal.kind = kind;
        goal.periodType = periodType;
        goal.startDate = startDate;
        goal.endDate = endDate;
        goal.rewardPoints = rewardPoints;
        goal.status = GoalStatus.ACTIVE;
        goal.habitId = habitId;
        goal.exerciseId = exerciseId;
        goal.targetValue = targetValue;
        goal.currentValue = BigDecimal.ZERO;
        return goal;
    }

    public void updateProgress(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public void complete(Instant when) {
        this.status = GoalStatus.COMPLETED;
        this.completedAt = when;
    }

    public void reopen() {
        this.status = GoalStatus.ACTIVE;
        this.completedAt = null;
    }

    public void fail() {
        this.status = GoalStatus.FAILED;
    }

    public void archive() {
        this.status = GoalStatus.ARCHIVED;
    }

    public void extend(LocalDate newEndDate) {
        this.status = GoalStatus.ACTIVE;
        this.endDate = newEndDate;
    }

    public boolean isComplete() {
        return targetValue != null && currentValue.compareTo(targetValue) >= 0;
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

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public GoalKind getKind() {
        return kind;
    }

    public GoalPeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getHabitId() {
        return habitId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }
}
