package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.PlanExercise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanExerciseRepository extends JpaRepository<PlanExercise, UUID> {

    List<PlanExercise> findAllByPlanIdOrderByDayIndexAscSortOrderAsc(UUID planId);

    List<PlanExercise> findAllByPlanIdAndDayIndexOrderBySortOrderAsc(UUID planId, int dayIndex);

    Optional<PlanExercise> findByIdAndPlanId(UUID id, UUID planId);

    List<PlanExercise> findAllByExerciseId(UUID exerciseId);

    void deleteAllByPlanId(UUID planId);
}
