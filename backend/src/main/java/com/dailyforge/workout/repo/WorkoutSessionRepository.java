package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.WorkoutSession;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    Optional<WorkoutSession> findByUserIdAndOccurredOnAndPlanIdAndDayIndex(
            UUID userId, LocalDate occurredOn, UUID planId, Integer dayIndex);

    Optional<WorkoutSession> findByIdAndUserId(UUID id, UUID userId);
}
