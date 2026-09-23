package com.dailyforge.goal.repo;

import com.dailyforge.goal.domain.Goal;
import com.dailyforge.goal.domain.GoalStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    List<Goal> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Goal> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, GoalStatus status);

    /** Rollover's own read: every still-ACTIVE goal whose window has closed (spec §8.6). */
    List<Goal> findAllByUserIdAndStatusAndEndDateLessThanEqual(UUID userId, GoalStatus status, LocalDate onOrBefore);
}
