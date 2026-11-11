package com.dailyforge.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * "I am finished with this exercise for this session."
 *
 * Deliberately a fact of its own rather than something inferred from the sets. Sets
 * logged tell you work happened, not that it finished, and there is no set count that
 * means "finished" for every exercise on every day — three sets is a full job for one
 * lift and a warm-up for another.
 */
@Entity
@Table(name = "workout_exercise_completion")
public class WorkoutExerciseCompletion {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected WorkoutExerciseCompletion() {
        // for JPA
    }

    public static WorkoutExerciseCompletion create(UUID sessionId, UUID exerciseId, Instant when) {
        WorkoutExerciseCompletion completion = new WorkoutExerciseCompletion();
        completion.id = UUID.randomUUID();
        completion.sessionId = sessionId;
        completion.exerciseId = exerciseId;
        completion.completedAt = when;
        return completion;
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

    public Instant getCompletedAt() {
        return completedAt;
    }
}
