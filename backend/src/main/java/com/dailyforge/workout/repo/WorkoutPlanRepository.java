package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.WorkoutPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, UUID> {

    List<WorkoutPlan> findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID userId);

    List<WorkoutPlan> findAllByUserIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(UUID userId);

    Optional<WorkoutPlan> findByIdAndUserId(UUID id, UUID userId);

    Optional<WorkoutPlan> findByUserIdAndActiveTrueAndArchivedAtIsNull(UUID userId);
}
