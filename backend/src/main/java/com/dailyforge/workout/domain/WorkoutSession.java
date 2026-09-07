package com.dailyforge.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day's worth of logging against (optionally) a plan and day. {@code planId} and
 * {@code dayIndex} are both null for a freeform session started without a plan — the
 * whole-session completion celebration (spec §8.3) only ever applies to a planned one,
 * since "every exercise for the day" has no meaning without a day to enumerate them from.
 */
@Entity
@Table(name = "workout_session")
public class WorkoutSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "day_index")
    private Integer dayIndex;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkoutSession() {
        // for JPA
    }

    public static WorkoutSession start(UUID userId, LocalDate occurredOn, UUID planId, Integer dayIndex) {
        WorkoutSession session = new WorkoutSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.occurredOn = occurredOn;
        session.planId = planId;
        session.dayIndex = dayIndex;
        session.startedAt = Instant.now();
        return session;
    }

    public void markCompleted(Instant when) {
        if (this.completedAt == null) {
            this.completedAt = when;
        }
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Integer getDayIndex() {
        return dayIndex;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
