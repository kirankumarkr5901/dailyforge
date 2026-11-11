package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.WorkoutExerciseCompletion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutExerciseCompletionRepository extends JpaRepository<WorkoutExerciseCompletion, UUID> {

    List<WorkoutExerciseCompletion> findAllBySessionId(UUID sessionId);

    Optional<WorkoutExerciseCompletion> findBySessionIdAndExerciseId(UUID sessionId, UUID exerciseId);

    void deleteBySessionIdAndExerciseId(UUID sessionId, UUID exerciseId);
}
